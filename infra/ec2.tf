# EC2 구성: 퍼블릭 서브넷의 EC2 한 대에서 앱만 Docker Compose로 실행하고, DB·Redis는 RDS·ElastiCache를 쓴다. (data_stores.tf)
# - 80 포트만 열고 SSH는 열지 않는다. 서버 접속과 배포는 SSM(Session Manager, Run Command)으로 한다.
# - 서버 디스크에는 데이터가 없어 서버를 다시 만들어도 잃는 것이 없다. DB 백업은 RDS 자동 백업이 맡는다.
# - 배포 중 앱이 다시 뜨는 동안(약 30초) 접속이 끊긴다.

locals {
  ec2_param_prefix = "/${var.project}/ec2"

  # 앱 환경변수 중 비밀이 아닌 것 (DB·Redis 주소 포함)
  ec2_app_environment = merge(local.common_app_environment, {
    # EC2 구성은 HTTP로만 서비스하므로 쿠키에 Secure를 붙이지 않는다.
    JWT_COOKIE_SECURE = "false"
  })
}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_security_group" "ec2" {
  count       = var.ec2_enabled ? 1 : 0
  name        = "${var.project}-ec2"
  description = "EC2 app server: allow HTTP from the internet"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "ec2_http" {
  count             = var.ec2_enabled ? 1 : 0
  security_group_id = aws_security_group.ec2[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_egress_rule" "ec2_all" {
  count             = var.ec2_enabled ? 1 : 0
  security_group_id = aws_security_group.ec2[0].id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

# ---------- 배포 스크립트·설정 (SSM 파라미터) ----------
# deploy.sh가 배포할 때마다 최신 값을 받아 쓰므로, 이 값들을 바꾼 뒤 apply하고 다시 배포하면 반영된다.

resource "aws_ssm_parameter" "ec2_compose" {
  count = var.ec2_enabled ? 1 : 0
  name  = "${local.ec2_param_prefix}/compose"
  type  = "String"
  tier  = "Intelligent-Tiering"
  value = file("${path.module}/ec2/docker-compose.yml")
}

resource "aws_ssm_parameter" "ec2_deploy_script" {
  count = var.ec2_enabled ? 1 : 0
  name  = "${local.ec2_param_prefix}/deploy-script"
  type  = "String"
  tier  = "Intelligent-Tiering"
  value = file("${path.module}/ec2/deploy.sh")
}

resource "aws_ssm_parameter" "ec2_app_env" {
  count = var.ec2_enabled ? 1 : 0
  name  = "${local.ec2_param_prefix}/app-env"
  type  = "String"
  tier  = "Intelligent-Tiering"
  value = jsonencode(local.ec2_app_environment)
}

# ---------- 서버 역할 ----------

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  count              = var.ec2_enabled ? 1 : 0
  name               = "${var.project}-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# SSM 접속·명령 실행
resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  count      = var.ec2_enabled ? 1 : 0
  role       = aws_iam_role.ec2[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# ECR에서 이미지 받기
resource "aws_iam_role_policy_attachment" "ec2_ecr" {
  count      = var.ec2_enabled ? 1 : 0
  role       = aws_iam_role.ec2[0].name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy" "ec2_secrets" {
  count  = var.ec2_enabled ? 1 : 0
  name   = "read-app-secrets"
  role   = aws_iam_role.ec2[0].id
  policy = data.aws_iam_policy_document.read_app_secrets.json
}

data "aws_iam_policy_document" "ec2_params" {
  statement {
    actions   = ["ssm:GetParameter"]
    resources = ["arn:aws:ssm:${var.region}:${data.aws_caller_identity.current.account_id}:parameter${local.ec2_param_prefix}/*"]
  }
}

resource "aws_iam_role_policy" "ec2_params" {
  count  = var.ec2_enabled ? 1 : 0
  name   = "read-deploy-params"
  role   = aws_iam_role.ec2[0].id
  policy = data.aws_iam_policy_document.ec2_params.json
}

resource "aws_iam_instance_profile" "ec2" {
  count = var.ec2_enabled ? 1 : 0
  name  = "${var.project}-ec2"
  role  = aws_iam_role.ec2[0].name
}

data "aws_caller_identity" "current" {}

# ---------- 서버 ----------

resource "aws_instance" "app" {
  count = var.ec2_enabled ? 1 : 0

  ami                    = data.aws_ssm_parameter.al2023_ami.value
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.ec2[0].id]
  iam_instance_profile   = aws_iam_instance_profile.ec2[0].name

  user_data = templatefile("${path.module}/ec2/user_data.sh.tftpl", {
    compose_version = "v2.29.7"
    region          = var.region
    registry        = split("/", aws_ecr_repository.app.repository_url)[0]
    param_prefix    = local.ec2_param_prefix
    app_secret_id   = aws_secretsmanager_secret.app.name
    db_secret_id    = aws_secretsmanager_secret.db.name
  })

  # IMDSv2만 허용
  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_type = "gp3"
    volume_size = var.ec2_volume_size
    encrypted   = true
    tags        = { Name = "${var.project}-ec2-root" }
  }

  tags = { Name = "${var.project}-app" }

  # 새 AMI가 나오거나 user_data를 고쳐도 apply 때마다 서버가 바뀌어 접속이 끊기지 않게 한다.
  # 서버를 새로 만들고 싶으면 terraform apply -replace=aws_instance.app[0] 후 다시 배포한다.
  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

# 서버를 껐다 켜도 주소가 바뀌지 않도록 고정 IP를 붙인다.
resource "aws_eip" "ec2" {
  count    = var.ec2_enabled ? 1 : 0
  domain   = "vpc"
  instance = aws_instance.app[0].id
  tags     = { Name = "${var.project}-app" }
}
