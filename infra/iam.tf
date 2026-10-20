# ECS 구성의 IAM 역할. EC2 구성의 역할은 ec2.tf에 있다.

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

# ---------- 실행 역할: ECS가 컨테이너를 띄울 때 쓴다 (이미지 받기, 로그, 비밀값 읽기) ----------

resource "aws_iam_role" "ecs_execution" {
  count              = var.ecs_enabled ? 1 : 0
  name               = "${var.project}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  count      = var.ecs_enabled ? 1 : 0
  role       = aws_iam_role.ecs_execution[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "read_app_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db.arn, aws_secretsmanager_secret.app.arn]
  }
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  count  = var.ecs_enabled ? 1 : 0
  name   = "read-app-secrets"
  role   = aws_iam_role.ecs_execution[0].id
  policy = data.aws_iam_policy_document.read_app_secrets.json
}

# ---------- 작업 역할: 실행 중인 앱이 AWS API를 부를 때 쓴다 ----------
# 지금 앱은 AWS API를 쓰지 않으므로 ECS Exec(컨테이너 접속)에 필요한 권한만 준다.

resource "aws_iam_role" "ecs_task" {
  count              = var.ecs_enabled ? 1 : 0
  name               = "${var.project}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "ecs_exec" {
  statement {
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "ecs_exec" {
  count  = var.ecs_enabled ? 1 : 0
  name   = "ecs-exec"
  role   = aws_iam_role.ecs_task[0].id
  policy = data.aws_iam_policy_document.ecs_exec.json
}
