# ALB (ECS 구성): HTTP API와 WebSocket(/ws)을 같은 주소로 받아 앱 컨테이너들에 나눠 보낸다.
# 채팅 메시지는 Redis Pub/Sub으로 서버 간에 전달되므로 고정 세션(sticky session)은 쓰지 않는다.

locals {
  https_enabled = var.certificate_arn != ""
}

resource "aws_lb" "main" {
  count = var.ecs_enabled ? 1 : 0

  name               = "${var.project}-alb"
  load_balancer_type = "application"
  subnets            = aws_subnet.public[*].id
  security_groups    = [aws_security_group.alb.id]

  # WebSocket 연결이 조용할 때 끊기지 않도록 기본값(60초)보다 길게 둔다.
  idle_timeout = 300
}

resource "aws_lb_target_group" "app" {
  count = var.ecs_enabled ? 1 : 0

  name        = "${var.project}-app"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  # 배포 때 기존 서버로 가던 요청이 끝날 시간을 준다.
  deregistration_delay = 30

  health_check {
    path                = "/actuator/health/liveness"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

# HTTPS를 켜면 80은 443으로 돌리고, 아니면 80에서 그대로 받는다.
resource "aws_lb_listener" "http" {
  count = var.ecs_enabled ? 1 : 0

  load_balancer_arn = aws_lb.main[0].arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = local.https_enabled ? "redirect" : "forward"
    target_group_arn = local.https_enabled ? null : aws_lb_target_group.app[0].arn

    dynamic "redirect" {
      for_each = local.https_enabled ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_lb_listener" "https" {
  count = var.ecs_enabled && local.https_enabled ? 1 : 0

  load_balancer_arn = aws_lb.main[0].arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app[0].arn
  }
}
