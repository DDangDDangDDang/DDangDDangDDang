# 비밀값은 Secrets Manager에 두고, ECS가 컨테이너를 시작할 때 환경변수로 넣어 준다.
# 이미지나 작업 정의(평문)에는 비밀값이 남지 않는다.

# EC2·ECS 구성이 같은 시크릿을 쓴다. 구성을 바꿔도 값을 다시 넣지 않아도 된다.

# DB 접속 정보: Terraform이 값까지 관리한다.
resource "aws_secretsmanager_secret" "db" {
  name        = "${var.project}/db"
  description = "MySQL 접속 정보 (Terraform이 관리)"
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = local.db_username
    password = random_password.db.result
  })
}

# 앱 비밀값(JWT, LiveKit, 포트원 등): 그릇만 만들고 값은 직접 넣는다. (docs/deploy.md 참고)
# Terraform 상태 파일에 외부 서비스 키가 남지 않도록 값은 Terraform으로 관리하지 않는다.
resource "aws_secretsmanager_secret" "app" {
  name        = "${var.project}/app"
  description = "앱 비밀값. 키 목록은 infra/variables.tf의 app_secret_keys 참고"
}
