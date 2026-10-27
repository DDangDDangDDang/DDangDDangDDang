# 트래픽 흐름
# - EC2 구성: 인터넷 → EC2(80) → DB(3306) / Redis(6379)
# - ECS 구성: 인터넷 → ALB(80/443) → 앱(8080) → DB(3306) / Redis(6379)
# 앞 단계에서 오는 트래픽만 허용하고, DB와 Redis는 인터넷에서 직접 접근할 수 없다.
# 보안 그룹은 비용이 없어 구성과 관계없이 만들어 둔다. EC2 서버의 보안 그룹은 ec2.tf에 있다.

resource "aws_security_group" "alb" {
  name        = "${var.project}-alb"
  description = "ALB: allow HTTP/HTTPS from the internet"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

resource "aws_security_group" "app" {
  name        = "${var.project}-app"
  description = "App containers: allow 8080 from ALB only"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  referenced_security_group_id = aws_security_group.alb.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
}

# 앱은 DB·Redis와 외부 API(NAT 경유), ECR·Secrets Manager에 접속해야 하므로 나가는 트래픽은 모두 허용한다.
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "db" {
  name        = "${var.project}-db"
  description = "RDS MySQL: allow 3306 from app only"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}

resource "aws_vpc_security_group_ingress_rule" "db_from_ec2" {
  count                        = var.ec2_enabled ? 1 : 0
  security_group_id            = aws_security_group.db.id
  referenced_security_group_id = aws_security_group.ec2[0].id
  ip_protocol                  = "tcp"
  from_port                    = 3306
  to_port                      = 3306
}

resource "aws_security_group" "redis" {
  name        = "${var.project}-redis"
  description = "ElastiCache Redis: allow 6379 from app only"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_app" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_ec2" {
  count                        = var.ec2_enabled ? 1 : 0
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.ec2[0].id
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
}
