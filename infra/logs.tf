# EC2 구성(운영 EC2, dev 서버)의 컨테이너 로그를 CloudWatch Logs로 보낸다.
# 서버 디스크(json-file)에만 두면 배포 때 컨테이너를 새로 만들 때마다 이전 로그가 사라져, 장애 원인을 배포 뒤에 찾을 수 없다.
# - 로그 그룹은 서버마다 하나(/ddang/ec2, /ddang/dev), 스트림은 컨테이너 이름(app, web 등)이다.
# - 서버의 docker-compose가 awslogs 드라이버로 보낸다. 그룹 이름은 SSM 파라미터(log-group)로 넘기고 deploy.sh가 .env에 넣는다.
# - Docker의 이중 로깅(기본 켜짐) 덕분에 서버에서 docker compose logs도 계속 볼 수 있다. (deploy.sh의 실패 로그 출력)
# ECS 구성은 ecs.tf의 /ecs/ddang-app 로그 그룹을 쓴다.

resource "aws_cloudwatch_log_group" "ec2" {
  count             = var.ec2_enabled ? 1 : 0
  name              = "/${var.project}/ec2"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "dev" {
  count             = var.dev_enabled ? 1 : 0
  name              = "/${var.project}/dev"
  retention_in_days = var.dev_log_retention_days
}

# 서버가 자기 로그 그룹에만 쓸 수 있다. (그룹은 Terraform이 만들므로 CreateLogGroup은 주지 않는다)
data "aws_iam_policy_document" "ec2_logs" {
  count = var.ec2_enabled ? 1 : 0

  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
    resources = ["${aws_cloudwatch_log_group.ec2[0].arn}:*"]
  }
}

resource "aws_iam_role_policy" "ec2_logs" {
  count  = var.ec2_enabled ? 1 : 0
  name   = "write-logs"
  role   = aws_iam_role.ec2[0].id
  policy = data.aws_iam_policy_document.ec2_logs[0].json
}

data "aws_iam_policy_document" "dev_logs" {
  count = var.dev_enabled ? 1 : 0

  statement {
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
    resources = ["${aws_cloudwatch_log_group.dev[0].arn}:*"]
  }
}

resource "aws_iam_role_policy" "dev_logs" {
  count  = var.dev_enabled ? 1 : 0
  name   = "write-logs"
  role   = aws_iam_role.dev[0].id
  policy = data.aws_iam_policy_document.dev_logs[0].json
}

# deploy.sh가 읽어 docker-compose의 awslogs-group으로 쓴다.
resource "aws_ssm_parameter" "ec2_log_group" {
  count = var.ec2_enabled ? 1 : 0
  name  = "${local.ec2_param_prefix}/log-group"
  type  = "String"
  value = aws_cloudwatch_log_group.ec2[0].name
}

resource "aws_ssm_parameter" "dev_log_group" {
  count = var.dev_enabled ? 1 : 0
  name  = "${local.dev_param_prefix}/log-group"
  type  = "String"
  value = aws_cloudwatch_log_group.dev[0].name
}
