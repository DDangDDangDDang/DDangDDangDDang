output "app_url" {
  description = "서비스 주소. 둘 다 켜져 있으면 ECS(ALB) 주소"
  value = (
    var.ecs_enabled ? "${local.https_enabled ? "https" : "http"}://${aws_lb.main[0].dns_name}" :
    var.ec2_enabled ? local.ec2_url : null
  )
}

locals {
  ec2_url = var.ec2_enabled ? (local.ec2_https ? "https://${var.domain_name}" : "http://${aws_eip.ec2[0].public_ip}") : null
}

output "ec2_url" {
  description = "EC2 구성 주소. domain_name이 있으면 https://<도메인>"
  value       = local.ec2_url
}

output "ec2_public_ip" {
  description = "EC2 고정 IP. 도메인 DNS의 A 레코드가 가리킬 주소 (manage_dns_record = true면 Terraform이 등록한다)"
  value       = var.ec2_enabled ? aws_eip.ec2[0].public_ip : null
}

output "ec2_instance_id" {
  description = "EC2 인스턴스 ID (SSM 접속: aws ssm start-session --target <ID>)"
  value       = var.ec2_enabled ? aws_instance.app[0].id : null
}

output "alb_dns_name" {
  description = "ECS 구성에서 도메인을 연결할 때 CNAME(또는 Route 53 별칭)으로 가리킬 주소"
  value       = var.ecs_enabled ? aws_lb.main[0].dns_name : null
}

output "rds_address" {
  description = "RDS 주소 (EC2 서버에서 직접 접속할 때 쓴다)"
  value       = aws_db_instance.main.address
}

output "redis_address" {
  description = "ElastiCache Redis 주소"
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "app_secret_name" {
  description = "앱 비밀값을 넣을 Secrets Manager 이름"
  value       = aws_secretsmanager_secret.app.name
}

# 아래 값들은 GitHub 저장소 Settings → Environments → production → Variables 에 넣는다.
# DEPLOY_TARGET만은 저장소 변수(Settings → Secrets and variables → Actions → Variables)에 넣는다.
output "github_variables" {
  description = "GitHub에 등록할 값 (켜져 있는 구성 것만)"
  value = merge(
    {
      AWS_REGION          = var.region
      AWS_DEPLOY_ROLE_ARN = aws_iam_role.github_deploy.arn
      ECR_REPOSITORY      = aws_ecr_repository.app.name
    },
    var.ec2_enabled ? {
      EC2_INSTANCE_ID    = aws_instance.app[0].id
      ECR_WEB_REPOSITORY = aws_ecr_repository.web.name
    } : {},
    var.ecs_enabled ? {
      ECS_CLUSTER        = aws_ecs_cluster.main[0].name
      ECS_SERVICE        = aws_ecs_service.app[0].name
      ECS_TASK_FAMILY    = aws_ecs_task_definition.app[0].family
      ECS_CONTAINER_NAME = local.container_name
    } : {},
  )
}

output "dev_url" {
  description = "dev 사이트 주소"
  value       = var.dev_enabled ? local.dev_origin : null
}

# GitHub 저장소 Settings → Environments → development → Variables 에 넣는다.
output "github_variables_dev" {
  description = "GitHub development Environment에 등록할 값"
  value = var.dev_enabled ? {
    AWS_REGION          = var.region
    AWS_DEPLOY_ROLE_ARN = aws_iam_role.github_deploy_dev[0].arn
    ECR_REPOSITORY      = aws_ecr_repository.dev["app"].name
    ECR_WEB_REPOSITORY  = aws_ecr_repository.dev["web"].name
    EC2_INSTANCE_ID     = aws_instance.dev[0].id
  } : null
}

output "team_db_access" {
  description = "팀원 DB 접속에 쓰는 값 (docs/deploy.md \"팀원 DB 접속\")"
  value = {
    prod_instance_id     = join("", aws_instance.app[*].id)
    prod_rds_address     = aws_db_instance.main.address
    prod_readonly_secret = aws_secretsmanager_secret.db_readonly.name
    dev_instance_id      = join("", aws_instance.dev[*].id)
    dev_db_secret        = join("", aws_secretsmanager_secret.dev_db[*].name)
    developers_iam_group = aws_iam_group.developers.name
  }
}
