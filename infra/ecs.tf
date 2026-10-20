# ECS 구성: Fargate에서 앱 컨테이너를 여러 개 실행하고 ALB 뒤에 둔다.

locals {
  container_name = "app"

  ecs_container_secrets = concat(
    [
      { name = "DB_USERNAME", valueFrom = "${aws_secretsmanager_secret.db.arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_secretsmanager_secret.db.arn}:password::" },
    ],
    [for key in var.app_secret_keys : { name = key, valueFrom = "${aws_secretsmanager_secret.app.arn}:${key}::" }],
  )
}

resource "aws_ecs_cluster" "main" {
  count = var.ecs_enabled ? 1 : 0
  name  = var.project

  setting {
    name  = "containerInsights"
    value = "disabled" # 비용 절감. 필요하면 enabled로 바꾼다.
  }
}

resource "aws_cloudwatch_log_group" "app" {
  count             = var.ecs_enabled ? 1 : 0
  name              = "/ecs/${var.project}-app"
  retention_in_days = 14
}

# 처음 만들 때의 작업 정의. 이후 이미지 교체는 GitHub Actions(deploy.yml)가 새 리비전을 등록해서 한다.
# 환경변수·비밀값을 바꾸려면 여기를 고쳐 apply한 뒤, 배포 워크플로를 한 번 더 실행해 새 이미지와 합친다.
resource "aws_ecs_task_definition" "app" {
  count = var.ecs_enabled ? 1 : 0

  family                   = "${var.project}-app"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.app_cpu
  memory                   = var.app_memory
  execution_role_arn       = aws_iam_role.ecs_execution[0].arn
  task_role_arn            = aws_iam_role.ecs_task[0].arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = local.container_name
    image     = "${aws_ecr_repository.app.repository_url}:latest"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [for k, v in merge(local.common_app_environment, {
      DB_URL     = "jdbc:mysql://${aws_db_instance.main[0].address}:${aws_db_instance.main[0].port}/${local.db_name}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
      REDIS_HOST = aws_elasticache_cluster.redis[0].cache_nodes[0].address
      REDIS_PORT = tostring(aws_elasticache_cluster.redis[0].port)
      # HTTPS일 때만 쿠키에 Secure를 붙인다. (HTTP에서 Secure 쿠키는 브라우저가 보내지 않음)
      JWT_COOKIE_SECURE = tostring(local.https_enabled)
    }) : { name = k, value = v }]
    secrets = local.ecs_container_secrets

    # 종료 신호 후 진행 중인 요청을 마무리할 시간 (server.shutdown=graceful)
    stopTimeout = 30

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app[0].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "app"
      }
    }
  }])
}

resource "aws_ecs_service" "app" {
  count = var.ecs_enabled ? 1 : 0

  name            = "${var.project}-app"
  cluster         = aws_ecs_cluster.main[0].id
  task_definition = aws_ecs_task_definition.app[0].arn
  desired_count   = var.app_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app[0].arn
    container_name   = local.container_name
    container_port   = 8080
  }

  # Spring Boot가 뜨는 동안 헬스 체크 실패로 내려가지 않도록 기다린다.
  health_check_grace_period_seconds = 180

  # 롤링 배포: 새 서버가 정상이 된 뒤 기존 서버를 내린다. 새 버전이 계속 실패하면 이전 버전으로 되돌린다.
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # 운영 중 컨테이너에 접속해 확인할 수 있게 한다. (aws ecs execute-command)
  enable_execute_command = true

  # 이미지 교체는 배포 워크플로가 하므로, Terraform이 작업 정의를 처음 버전으로 되돌리지 않게 한다.
  lifecycle {
    ignore_changes = [task_definition]
  }

  # HTTPS를 켜면 대상 그룹을 ALB에 연결하는 것은 HTTPS 리스너이므로 두 리스너가 모두 만들어진 뒤 서비스를 만든다.
  depends_on = [aws_lb_listener.http, aws_lb_listener.https]
}
