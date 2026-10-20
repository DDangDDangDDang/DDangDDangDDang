# GitHub Actions가 AWS 액세스 키 없이 배포하도록 OIDC로 역할을 빌려 쓴다.
# 이 저장소의 지정한 GitHub Environment(production)에서 실행된 작업만 이 역할을 쓸 수 있다.

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

locals {
  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:environment:${var.github_environment}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${var.project}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

# 배포에 필요한 최소 권한: 이미지 올리기 + 켜져 있는 구성에 배포하기
data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "EcrLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [aws_ecr_repository.app.arn]
  }

  # ---- EC2 구성: SSM Run Command로 서버에서 deploy.sh 실행 ----

  dynamic "statement" {
    for_each = var.ec2_enabled ? [1] : []
    content {
      sid     = "Ec2RunDeployCommand"
      actions = ["ssm:SendCommand"]
      resources = [
        "arn:aws:ssm:${var.region}::document/AWS-RunShellScript",
        aws_instance.app[0].arn,
      ]
    }
  }

  dynamic "statement" {
    for_each = var.ec2_enabled ? [1] : []
    content {
      sid       = "Ec2ReadCommandResult"
      actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
      resources = ["*"]
    }
  }

  # ---- ECS 구성: 작업 정의 등록, 서비스 갱신 ----

  dynamic "statement" {
    for_each = var.ecs_enabled ? [1] : []
    content {
      sid = "EcsTaskDefinition"
      # 작업 정의 API는 리소스 단위 권한을 지원하지 않는다.
      actions   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"]
      resources = ["*"]
    }
  }

  dynamic "statement" {
    for_each = var.ecs_enabled ? [1] : []
    content {
      sid       = "EcsDeploy"
      actions   = ["ecs:UpdateService", "ecs:DescribeServices"]
      resources = [aws_ecs_service.app[0].id]
    }
  }

  dynamic "statement" {
    for_each = var.ecs_enabled ? [1] : []
    content {
      sid       = "PassTaskRoles"
      actions   = ["iam:PassRole"]
      resources = [aws_iam_role.ecs_execution[0].arn, aws_iam_role.ecs_task[0].arn]

      condition {
        test     = "StringEquals"
        variable = "iam:PassedToService"
        values   = ["ecs-tasks.amazonaws.com"]
      }
    }
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy-ecs"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
