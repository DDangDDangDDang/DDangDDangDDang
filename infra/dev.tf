# dev 사이트 (dev.<도메인>): dev 브랜치에 푸시하면 이 서버로 배포한다. (.github/workflows/deploy.yml)
# - 운영과 완전히 분리한다: EC2·ECR·비밀값·배포 역할을 따로 두고, MySQL·Redis는 이 서버 안의 컨테이너로 띄운다.
#   (RDS·ElastiCache를 쓰지 않아 월 약 $20. dev 데이터는 서버 디스크에만 있어 서버를 다시 만들면 사라진다)
# - dev 배포 역할은 dev 서버에만 배포할 수 있고, 운영 서버·운영 이미지 저장소에는 접근하지 못한다.
# - 쓰지 않을 때는 EC2를 꺼 두면(aws ec2 stop-instances) 디스크·고정 IP 비용만 든다.

locals {
  dev_param_prefix = "/${var.project}/dev"
  dev_domain       = var.domain_name == "" ? "" : "dev.${var.domain_name}"
  dev_https        = local.dev_domain != ""
  dev_public_ip    = join("", aws_eip.dev[*].public_ip)
  dev_origin       = local.dev_https ? "https://${local.dev_domain}" : "http://${local.dev_public_ip}"

  # 앱 환경변수 중 비밀이 아닌 것. DB·Redis는 같은 서버의 컨테이너(docker-compose.dev.yml)를 쓴다.
  dev_app_environment = merge(var.dev_app_environment, {
    SPRING_PROFILES_ACTIVE = "prod"
    DDL_AUTO               = "update"
    LIVEKIT_API_URL        = replace(local.dev_livekit_url, "wss://", "https://")
    LIVEKIT_WS_URL         = local.dev_livekit_url
    DB_URL                 = "jdbc:mysql://mysql:3306/${local.db_name}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
    REDIS_HOST             = "redis"
    REDIS_PORT             = "6379"
    JWT_COOKIE_SECURE      = tostring(local.dev_https)
    KAKAO_REDIRECT_URI     = "${local.dev_origin}/oauth/kakao/callback"
    GOOGLE_REDIRECT_URI    = "${local.dev_origin}/oauth/google/callback"
  })

  dev_livekit_url = var.dev_livekit_url != "" ? var.dev_livekit_url : var.livekit_url
}

# ---------- 이미지 저장소 (운영과 분리) ----------

resource "aws_ecr_repository" "dev" {
  for_each             = var.dev_enabled ? toset(["app", "web"]) : toset([])
  name                 = "${var.project}-dev-${each.key}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true # dev 이미지는 지워도 된다. (dev를 끌 때 저장소를 바로 지울 수 있게)
}

resource "aws_ecr_lifecycle_policy" "dev" {
  for_each   = aws_ecr_repository.dev
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 이미지 10개만 남긴다"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ---------- 비밀값 ----------

# 앱 비밀값: 키 목록은 운영(ddang/app)과 같다. 값은 직접 넣는다. (운영 값을 그대로 쓰지 말 것, 특히 JWT_SECRET)
# 사람이 넣은 값이라 Terraform이 다시 만들 수 없어 삭제 복구 기간(기본 30일)을 둔다.
# 그 기간에 다시 켜려면 복구 후 import한다. (docs/deploy.md "dev 사이트" → 완전히 없앤 뒤 다시 켜기)
resource "aws_secretsmanager_secret" "dev_app" {
  count       = var.dev_enabled ? 1 : 0
  name        = "${var.project}-dev/app"
  description = "dev 사이트 앱 비밀값. 키 목록은 infra/variables.tf의 app_secret_keys 참고"
}

# dev 서버 안의 MySQL 컨테이너 접속 정보 (Terraform이 관리)
resource "random_password" "dev_db" {
  count   = var.dev_enabled ? 1 : 0
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "dev_db" {
  count       = var.dev_enabled ? 1 : 0
  name        = "${var.project}-dev/db"
  description = "dev 서버 MySQL 컨테이너 접속 정보 (Terraform이 관리)"
  # Terraform이 만든 값이라 잃을 것이 없다. 바로 지워서 dev를 껐다 다시 켤 때 같은 이름으로 만들 수 있게 한다.
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "dev_db" {
  count     = var.dev_enabled ? 1 : 0
  secret_id = aws_secretsmanager_secret.dev_db[0].id
  secret_string = jsonencode({
    username = local.db_username
    password = random_password.dev_db[0].result
  })
}

# ---------- 배포 스크립트·설정 (SSM 파라미터, 운영과 같은 deploy.sh) ----------

resource "aws_ssm_parameter" "dev_compose" {
  count = var.dev_enabled ? 1 : 0
  name  = "${local.dev_param_prefix}/compose"
  type  = "String"
  tier  = "Intelligent-Tiering"
  value = file("${path.module}/ec2/docker-compose.dev.yml")

  lifecycle {
    precondition {
      condition     = !strcontains(file("${path.module}/ec2/docker-compose.dev.yml"), "{{")
      error_message = "infra/ec2/docker-compose.dev.yml에 이중 중괄호를 쓸 수 없습니다. (SSM 파라미터 제한)"
    }
  }
}

resource "aws_ssm_parameter" "dev_deploy_script" {
  count = var.dev_enabled ? 1 : 0
  name  = "${local.dev_param_prefix}/deploy-script"
  type  = "String"
  tier  = "Intelligent-Tiering"
  value = file("${path.module}/ec2/deploy.sh")

  lifecycle {
    precondition {
      condition     = !strcontains(file("${path.module}/ec2/deploy.sh"), "{{")
      error_message = "infra/ec2/deploy.sh에 이중 중괄호를 쓸 수 없습니다. (SSM 파라미터 제한)"
    }
  }
}

resource "aws_ssm_parameter" "dev_app_env" {
  count = var.dev_enabled ? 1 : 0
  name  = "${local.dev_param_prefix}/app-env"
  type  = "String"
  tier  = "Intelligent-Tiering"
  value = jsonencode(local.dev_app_environment)
}

resource "aws_ssm_parameter" "dev_web_env" {
  count = var.dev_enabled ? 1 : 0
  name  = "${local.dev_param_prefix}/web-env"
  type  = "String"
  tier  = "Intelligent-Tiering"
  value = jsonencode({ SITE_ADDRESS = local.dev_https ? local.dev_domain : ":80" })
}

# ---------- 서버 ----------

resource "aws_security_group" "dev" {
  count       = var.dev_enabled ? 1 : 0
  name        = "${var.project}-dev"
  description = "dev site server: allow HTTP/HTTPS from the internet"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "dev_web" {
  for_each = var.dev_enabled ? {
    http      = { protocol = "tcp", port = 80 }
    https     = { protocol = "tcp", port = 443 }
    https_udp = { protocol = "udp", port = 443 }
  } : {}
  security_group_id = aws_security_group.dev[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = each.value.protocol
  from_port         = each.value.port
  to_port           = each.value.port
}

resource "aws_vpc_security_group_egress_rule" "dev_all" {
  count             = var.dev_enabled ? 1 : 0
  security_group_id = aws_security_group.dev[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_iam_role" "dev" {
  count              = var.dev_enabled ? 1 : 0
  name               = "${var.project}-dev-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "dev_ssm" {
  count      = var.dev_enabled ? 1 : 0
  role       = aws_iam_role.dev[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# dev 서버가 읽을 수 있는 것: dev 이미지, dev 비밀값, dev 배포 설정만
data "aws_iam_policy_document" "dev_server" {
  count = var.dev_enabled ? 1 : 0

  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions   = ["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"]
    resources = [for repo in aws_ecr_repository.dev : repo.arn]
  }

  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.dev_app[0].arn, aws_secretsmanager_secret.dev_db[0].arn]
  }

  statement {
    actions   = ["ssm:GetParameter"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.dev_param_prefix}/*"]
  }
}

resource "aws_iam_role_policy" "dev_server" {
  count  = var.dev_enabled ? 1 : 0
  name   = "dev-server"
  role   = aws_iam_role.dev[0].id
  policy = data.aws_iam_policy_document.dev_server[0].json
}

resource "aws_iam_instance_profile" "dev" {
  count = var.dev_enabled ? 1 : 0
  name  = "${var.project}-dev-ec2"
  role  = aws_iam_role.dev[0].name
}

resource "aws_instance" "dev" {
  count = var.dev_enabled ? 1 : 0

  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.dev_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.dev[0].id]
  iam_instance_profile   = aws_iam_instance_profile.dev[0].name

  user_data = templatefile("${path.module}/ec2/user_data.sh.tftpl", {
    compose_version = "v2.29.7"
    region          = var.region
    registry        = split("/", aws_ecr_repository.dev["app"].repository_url)[0]
    param_prefix    = local.dev_param_prefix
    app_secret_id   = aws_secretsmanager_secret.dev_app[0].name
    db_secret_id    = aws_secretsmanager_secret.dev_db[0].name
  })

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
    encrypted   = true
    tags        = { Name = "${var.project}-dev-root" }
  }

  tags = { Name = "${var.project}-dev" }

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "dev" {
  count    = var.dev_enabled ? 1 : 0
  domain   = "vpc"
  instance = aws_instance.dev[0].id
  tags     = { Name = "${var.project}-dev" }
}

resource "aws_route53_record" "dev" {
  count   = var.dev_enabled && var.manage_dns_record ? 1 : 0
  zone_id = data.aws_route53_zone.main[0].zone_id
  name    = local.dev_domain
  type    = "A"
  ttl     = 300
  records = [aws_eip.dev[0].public_ip]
}

# ---------- GitHub Actions dev 배포 역할 (GitHub Environment "development"만) ----------

data "aws_iam_policy_document" "github_dev_assume" {
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
      values = compact([
        "repo:${var.github_repository}:environment:${local.github_oidc_dev_environment}",
        var.github_oidc_subject_prefix == "" ? "" : "${var.github_oidc_subject_prefix}:environment:${local.github_oidc_dev_environment}",
      ])
    }
  }
}

locals {
  github_oidc_dev_environment = replace(var.github_dev_environment, ":", "%3A")
}

resource "aws_iam_role" "github_deploy_dev" {
  count              = var.dev_enabled ? 1 : 0
  name               = "${var.project}-github-deploy-dev"
  assume_role_policy = data.aws_iam_policy_document.github_dev_assume.json
}

data "aws_iam_policy_document" "github_deploy_dev" {
  count = var.dev_enabled ? 1 : 0

  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [for repo in aws_ecr_repository.dev : repo.arn]
  }

  statement {
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${var.region}::document/AWS-RunShellScript",
      aws_instance.dev[0].arn,
    ]
  }

  statement {
    actions   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_deploy_dev" {
  count  = var.dev_enabled ? 1 : 0
  name   = "deploy-dev"
  role   = aws_iam_role.github_deploy_dev[0].id
  policy = data.aws_iam_policy_document.github_deploy_dev[0].json
}
