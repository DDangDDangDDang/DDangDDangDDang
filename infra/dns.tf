# 도메인 A 레코드. Route 53에서 산 도메인일 때만 만든다. (구입하면 호스팅 영역이 자동으로 생긴다)
# dns_target으로 가리킬 구성을 고른다. 바꾸면 같은 레코드를 제자리에서 고쳐(UPSERT) 도메인이 끊기지 않는다.
#   ec2: EC2 고정 IP / ecs: ALB (별칭 레코드)
# ECS로 전환할 때는 EC2를 끄기 전에 dns_target = "ecs"로 먼저 옮긴다. (docs/deploy.md "ECS로 전환")

locals {
  dns_on_alb = var.dns_target == "ecs"
}

data "aws_route53_zone" "main" {
  count = var.manage_dns_record ? 1 : 0
  name  = var.domain_name

  lifecycle {
    precondition {
      condition     = var.domain_name != ""
      error_message = "manage_dns_record = true이면 domain_name도 넣어야 합니다."
    }
  }
}

resource "aws_route53_record" "main" {
  count   = var.manage_dns_record ? 1 : 0
  zone_id = data.aws_route53_zone.main[0].zone_id
  name    = var.domain_name
  type    = "A"
  ttl     = local.dns_on_alb ? null : 300
  records = local.dns_on_alb ? null : [one(aws_eip.ec2[*].public_ip)]

  dynamic "alias" {
    for_each = local.dns_on_alb ? [1] : []
    content {
      name                   = one(aws_lb.main[*].dns_name)
      zone_id                = one(aws_lb.main[*].zone_id)
      evaluate_target_health = false
    }
  }

  lifecycle {
    precondition {
      condition     = local.dns_on_alb ? var.ecs_enabled : var.ec2_enabled
      error_message = "dns_target이 가리키는 구성이 켜져 있어야 합니다. (ec2 → ec2_enabled, ecs → ecs_enabled) EC2를 끄기 전에 dns_target = \"ecs\"로 옮기세요."
    }
  }
}
