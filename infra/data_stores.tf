# DB 계정은 EC2 구성(MySQL 컨테이너)과 ECS 구성(RDS)이 같은 값을 쓴다. 전환할 때 앱 설정을 바꾸지 않아도 된다.
# 비밀번호는 Terraform이 만들어 Secrets Manager(ddang/db)에 넣고, 앱은 시작할 때 그 값을 받는다.
# (RDS가 관리하는 비밀번호는 7일마다 자동으로 바뀌어, 실행 중인 서버가 접속하지 못하게 되므로 쓰지 않는다.)
locals {
  db_name     = "ddang"
  db_username = "ddang"
}

resource "random_password" "db" {
  length  = 32
  special = false
}

# ---------- RDS MySQL (ECS 구성) ----------

resource "aws_db_subnet_group" "main" {
  count      = var.ecs_enabled ? 1 : 0
  name       = "${var.project}-db"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_parameter_group" "mysql" {
  count  = var.ecs_enabled ? 1 : 0
  name   = "${var.project}-mysql84"
  family = "mysql8.4"

  # 로컬 docker-compose·EC2 구성(MySQL 8.4)과 같은 문자셋·시간대
  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }

  parameter {
    name  = "time_zone"
    value = "Asia/Seoul"
  }
}

resource "aws_db_instance" "main" {
  count = var.ecs_enabled ? 1 : 0

  identifier     = "${var.project}-mysql"
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name  = local.db_name
  username = local.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main[0].name
  vpc_security_group_ids = [aws_security_group.db.id]
  parameter_group_name   = aws_db_parameter_group.mysql[0].name
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period   = 7
  deletion_protection       = var.db_deletion_protection
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project}-mysql-final"

  auto_minor_version_upgrade = true
  apply_immediately          = true
}

# ---------- ElastiCache Redis (ECS 구성) ----------
# 리프레시 토큰, 인증번호, 채팅 도배 제한, 서버 간 STOMP 메시지 전달(Pub/Sub)에 쓴다.
# 앱의 Redis 설정이 TLS·비밀번호를 쓰지 않으므로 전송 암호화는 끄고, 보안 그룹으로 앱에서만 접근하게 한다.

resource "aws_elasticache_subnet_group" "main" {
  count      = var.ecs_enabled ? 1 : 0
  name       = "${var.project}-redis"
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_cluster" "redis" {
  count = var.ecs_enabled ? 1 : 0

  cluster_id           = "${var.project}-redis"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.main[0].name
  security_group_ids = [aws_security_group.redis.id]

  snapshot_retention_limit = 1
}
