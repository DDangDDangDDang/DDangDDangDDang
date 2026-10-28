# 팀원 DB 접속: 팀원은 SSH·공개 포트 없이 Session Manager 포트 포워딩으로만 DB에 붙는다. (docs/deploy.md "팀원 DB 접속")
# - 운영: EC2를 거쳐 RDS로 터널(AWS-StartPortForwardingSessionToRemoteHost). 계정은 SELECT만 되는 읽기 전용 계정(ddang_ro).
# - dev: dev 서버의 MySQL 컨테이너(127.0.0.1:3306에만 열림)로 터널(AWS-StartPortForwardingSession). 계정은 dev 앱 계정.
# - 서버 셸 접속(SSM-SessionManagerRunShell)은 주지 않는다. 문서를 고르지 않고 접속하면 셸이 열리므로 문서 지정을 강제한다.
# - 팀원 IAM 사용자는 developer_iam_users에 이름을 넣으면 만든다. 액세스 키는 각자(또는 관리자가) 콘솔에서 만든다.
#   (Terraform으로 키를 만들면 상태 파일에 키가 남는다)

locals {
  db_readonly_username = "ddang_ro"

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

# 운영 서버가 읽기 전용 계정을 만들 때 비밀번호를 서버에서 바로 읽는다. (명령 기록에 비밀번호가 남지 않게)
data "aws_iam_policy_document" "ec2_read_db_readonly" {
  statement {
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [aws_secretsmanager_secret.db_readonly.arn]
  }
}

resource "aws_iam_role_policy" "ec2_read_db_readonly" {
  count  = var.ec2_enabled ? 1 : 0
  name   = "read-db-readonly-secret"
  role   = aws_iam_role.ec2[0].id
  policy = data.aws_iam_policy_document.ec2_read_db_readonly.json
}

# ---------- 팀원 IAM 그룹 ----------

resource "aws_iam_group" "developers" {
  name = "${var.project}-developers"
}

data "aws_iam_policy_document" "developers" {
  # 포트 포워딩 문서로만 세션을 연다. (문서를 지정하지 않으면 셸이 열리므로 SessionDocumentAccessCheck로 막는다)
  statement {
    sid     = "TunnelToServers"
    actions = ["ssm:StartSession"]
    resources = concat(local.team_tunnel_instance_arns, [
      "arn:aws:ssm:${var.region}::document/AWS-StartPortForwardingSessionToRemoteHost",
      "arn:aws:ssm:${var.region}::document/AWS-StartPortForwardingSession",
    ])
    condition {
      test     = "BoolIfExists"
      variable = "ssm:SessionDocumentAccessCheck"
      values   = ["true"]
    }
  }

  # 내가 연 세션만 끝내거나 다시 이을 수 있다.
  statement {
    sid       = "ManageOwnSessions"
    actions   = ["ssm:TerminateSession", "ssm:ResumeSession"]
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
  groups   = [aws_iam_group.developers.name]
}
