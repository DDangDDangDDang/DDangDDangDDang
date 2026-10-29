# 팀원 DB 접속: 팀원은 SSH·공개 포트 없이 Session Manager 포트 포워딩으로만 DB에 붙는다. (docs/deploy.md "팀원 DB 접속")
# - 운영: EC2를 거쳐 RDS로 터널(ddang-db-tunnel-prod 문서). 계정은 SELECT만 되는 읽기 전용 계정(ddang_ro).
#   데이터를 고쳐야 하는 일부 팀원(db_writer_iam_users)만 수정용 계정(ddang_rw)을 쓴다. 행 조회·추가·수정·삭제만 되고 테이블 구조는 못 바꾼다.
# - dev: dev 서버의 MySQL 컨테이너(127.0.0.1:3306에만 열림)로 터널(ddang-db-tunnel-dev 문서). 계정은 dev 앱 계정.
# - 터널 대상은 문서에 고정한다. 팀원은 내 PC 포트만 고른다. (아래 "DB 터널 전용 세션 문서")
# - 서버 셸 접속(SSM-SessionManagerRunShell)은 주지 않는다. 문서를 고르지 않고 접속하면 셸이 열리므로 문서 지정을 강제한다.
# - 팀원 IAM 사용자는 developer_iam_users에 이름을 넣으면 만든다. 액세스 키는 각자(또는 관리자가) 콘솔에서 만든다.
#   (Terraform으로 키를 만들면 상태 파일에 키가 남는다)

locals {
  db_readonly_username  = "ddang_ro"
  db_readwrite_username = "ddang_rw"

  # 터널을 열 수 있는 서버 (켜져 있는 구성만)
  team_tunnel_instance_arns = concat(
    [for i in aws_instance.app : i.arn],
    [for i in aws_instance.dev : i.arn],
  )
}

# ---------- 운영 읽기 전용 DB 계정 ----------
# 비밀번호만 Terraform이 만든다. MySQL 계정 자체는 운영 서버에서 한 번 만든다. (docs/deploy.md "팀원 DB 접속" 준비 2)

resource "random_password" "db_readonly" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db_readonly" {
  name        = "${var.project}/db-readonly"
  description = "운영 RDS 읽기 전용 계정 (팀원 조회용, Terraform이 관리)"
}

resource "aws_secretsmanager_secret_version" "db_readonly" {
  secret_id = aws_secretsmanager_secret.db_readonly.id
  secret_string = jsonencode({
    username = local.db_readonly_username
    password = random_password.db_readonly.result
  })
}

# ---------- 운영 수정용 DB 계정 ----------
# 행 조회·추가·수정·삭제만 된다. 테이블 구조 변경(CREATE·ALTER·DROP)과 계정 관리는 할 수 없다.
# 비밀번호는 db_writers 그룹만 읽는다. 수정 전에는 RDS 수동 스냅샷을 찍는다. (docs/deploy.md "팀원 DB 접속")

resource "random_password" "db_readwrite" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db_readwrite" {
  name        = "${var.project}/db-readwrite"
  description = "운영 RDS 수정용 계정 (데이터 수정 담당 팀원용, Terraform이 관리)"
}

resource "aws_secretsmanager_secret_version" "db_readwrite" {
  secret_id = aws_secretsmanager_secret.db_readwrite.id
  secret_string = jsonencode({
    username = local.db_readwrite_username
    password = random_password.db_readwrite.result
  })
}

# 운영 서버가 팀원용 계정을 만들 때 비밀번호를 서버에서 바로 읽는다. (명령 기록에 비밀번호가 남지 않게)
data "aws_iam_policy_document" "ec2_read_team_db_accounts" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db_readonly.arn, aws_secretsmanager_secret.db_readwrite.arn]
  }
}

resource "aws_iam_role_policy" "ec2_read_team_db_accounts" {
  count  = var.ec2_enabled ? 1 : 0
  name   = "read-team-db-account-secrets"
  role   = aws_iam_role.ec2[0].id
  policy = data.aws_iam_policy_document.ec2_read_team_db_accounts.json
}

# ---------- 팀원 IAM 그룹 ----------

resource "aws_iam_group" "developers" {
  name = "${var.project}-developers"
}

# 운영 데이터 수정 담당. developers 그룹 권한(터널)에 수정용 계정 비밀번호 읽기만 더한다.
resource "aws_iam_group" "db_writers" {
  name = "${var.project}-db-writers"
}

data "aws_iam_policy_document" "db_writers" {
  statement {
    sid       = "ReadWriteDbCredentials"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db_readwrite.arn]
  }
}

resource "aws_iam_group_policy" "db_writers" {
  name   = "db-readwrite-secret"
  group  = aws_iam_group.db_writers.name
  policy = data.aws_iam_policy_document.db_writers.json
}

# ---------- DB 터널 전용 세션 문서 ----------
# AWS 기본 문서(AWS-StartPortForwardingSession*)는 접속할 호스트·포트를 호출하는 사람이 정한다.
# 그러면 서버의 메타데이터(169.254.169.254, 인스턴스 역할 자격 증명)나 다른 포트로도 터널을 열 수 있으므로,
# 대상을 DB로 고정하고 내 PC 포트만 고를 수 있는 문서를 따로 만든다.

locals {
  local_port_parameter = {
    localPortNumber = {
      type           = "String"
      description    = "내 PC에서 열 포트"
      allowedPattern = "^([1-9][0-9]{0,4})$"
      default        = "13306"
    }
  }
}

resource "aws_ssm_document" "db_tunnel_prod" {
  name            = "${var.project}-db-tunnel-prod"
  document_type   = "Session"
  document_format = "JSON"
  content = jsonencode({
    schemaVersion = "1.0"
    description   = "운영 RDS(3306)로 가는 터널. 대상은 고정이고 내 PC 포트만 고른다."
    sessionType   = "Port"
    parameters    = local.local_port_parameter
    properties = {
      type            = "LocalPortForwarding"
      host            = aws_db_instance.main.address
      portNumber      = "3306"
      localPortNumber = "{{ localPortNumber }}"
    }
  })
}

resource "aws_ssm_document" "db_tunnel_dev" {
  count           = var.dev_enabled ? 1 : 0
  name            = "${var.project}-db-tunnel-dev"
  document_type   = "Session"
  document_format = "JSON"
  content = jsonencode({
    schemaVersion = "1.0"
    description   = "dev 서버 안 MySQL(127.0.0.1:3306)로 가는 터널. 대상은 고정이고 내 PC 포트만 고른다."
    sessionType   = "Port"
    parameters    = merge(local.local_port_parameter, { localPortNumber = merge(local.local_port_parameter.localPortNumber, { default = "13307" }) })
    properties = {
      type            = "LocalPortForwarding"
      portNumber      = "3306"
      localPortNumber = "{{ localPortNumber }}"
    }
  })
}

data "aws_iam_policy_document" "developers" {
  # DB 터널 문서로만 세션을 연다. (문서를 지정하지 않으면 셸이 열리므로 SessionDocumentAccessCheck로 막는다)
  # 운영 문서를 dev 서버에서 쓰거나 그 반대로 써도 대상이 DB로 고정돼 있어 닿는 곳이 없다.
  statement {
    sid     = "TunnelToDatabases"
    actions = ["ssm:StartSession"]
    resources = concat(
      local.team_tunnel_instance_arns,
      [aws_ssm_document.db_tunnel_prod.arn],
      [for d in aws_ssm_document.db_tunnel_dev : d.arn],
    )
    condition {
      test     = "BoolIfExists"
      variable = "ssm:SessionDocumentAccessCheck"
      values   = ["true"]
    }
  }

  # 내가 연 세션만 끝내거나 다시 잇고, 그 세션의 데이터 채널을 연다. (Session Manager 플러그인이 터널 데이터를 주고받을 때 필요)
  statement {
    sid       = "ManageOwnSessions"
    actions   = ["ssm:TerminateSession", "ssm:ResumeSession", "ssmmessages:OpenDataChannel"]
    resources = ["arn:aws:ssm:*:*:session/$${aws:username}-*"]
  }

  # 접속 계정 비밀번호: 운영은 읽기 전용 계정, dev는 dev 앱 계정
  statement {
    sid       = "ReadDbCredentials"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = concat([aws_secretsmanager_secret.db_readonly.arn], [for s in aws_secretsmanager_secret.dev_db : s.arn])
  }

  # 본인 액세스 키·비밀번호·MFA는 스스로 관리한다.
  statement {
    sid = "ManageOwnCredentials"
    actions = [
      "iam:GetUser",
      "iam:ChangePassword",
      "iam:CreateAccessKey",
      "iam:DeleteAccessKey",
      "iam:ListAccessKeys",
      "iam:UpdateAccessKey",
      "iam:ListMFADevices",
      "iam:CreateVirtualMFADevice",
      "iam:EnableMFADevice",
      "iam:ResyncMFADevice",
    ]
    resources = [
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:user/$${aws:username}",
      "arn:aws:iam::${data.aws_caller_identity.current.account_id}:mfa/$${aws:username}",
    ]
  }
}

resource "aws_iam_group_policy" "developers" {
  name   = "db-tunnel"
  group  = aws_iam_group.developers.name
  policy = data.aws_iam_policy_document.developers.json
}

resource "aws_iam_user" "developers" {
  for_each = toset(var.developer_iam_users)
  name     = each.value
}

resource "aws_iam_user_group_membership" "developers" {
  for_each = aws_iam_user.developers
  user     = each.value.name
  groups = concat(
    [aws_iam_group.developers.name],
    contains(var.db_writer_iam_users, each.key) ? [aws_iam_group.db_writers.name] : [],
  )
}
