output "app_url" {
  description = "서비스 주소. 둘 다 켜져 있으면 ECS(ALB) 주소"
  value = (
    var.ecs_enabled ? "${local.https_enabled ? "https" : "http"}://${aws_lb.main[0].dns_name}" :
    var.ec2_enabled ? "http://${aws_eip.ec2[0].public_ip}" : null
  )
}

output "ec2_url" {
  description = "EC2 구성 주소"
  value       = var.ec2_enabled ? "http://${aws_eip.ec2[0].public_ip}" : null
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
      EC2_INSTANCE_ID = aws_instance.app[0].id
    } : {},
    var.ecs_enabled ? {
      ECS_CLUSTER        = aws_ecs_cluster.main[0].name
      ECS_SERVICE        = aws_ecs_service.app[0].name
      ECS_TASK_FAMILY    = aws_ecs_task_definition.app[0].family
      ECS_CONTAINER_NAME = local.container_name
    } : {},
  )
}
