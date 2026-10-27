# 배포 구성 세 가지(EC2만, 전환 중, ECS만)가 AWS에 접속하지 않고도 계획(plan)까지 되는지 확인한다.
#   cd infra && terraform test

mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["ap-northeast-2a", "ap-northeast-2c"]
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }

  mock_resource "aws_ecr_repository" {
    defaults = {
      repository_url = "123456789012.dkr.ecr.ap-northeast-2.amazonaws.com/ddang-app"
      arn            = "arn:aws:ecr:ap-northeast-2:123456789012:repository/ddang-app"
    }
  }

  mock_resource "aws_iam_openid_connect_provider" {
    defaults = {
      arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com"
    }
  }

  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/mock"
    }
  }

  mock_resource "aws_instance" {
    defaults = {
      arn = "arn:aws:ec2:ap-northeast-2:123456789012:instance/i-0123456789abcdef0"
    }
  }

  # 앱 환경변수에 들어가는 주소를 plan 단계에서 확인할 수 있게 한다.
  mock_resource "aws_db_instance" {
    override_during = plan
    defaults = {
      address = "rds.mock"
      port    = 3306
    }
  }

  mock_resource "aws_eip" {
    override_during = plan
    defaults = {
      public_ip = "203.0.113.10"
    }
  }

  mock_resource "aws_elasticache_cluster" {
    override_during = plan
    defaults = {
      cache_nodes = [{ address = "redis.mock", availability_zone = "ap-northeast-2a", id = "0001", outpost_arn = "", port = 6379 }]
    }
  }
}

mock_provider "random" {}

variables {
  livekit_url = "wss://example.livekit.cloud"
}

run "ec2_only" {
  command = plan

  variables {
    ec2_enabled = true
    ecs_enabled = false
  }

  assert {
    condition     = length(aws_instance.app) == 1 && length(aws_nat_gateway.main) == 0 && length(aws_lb.main) == 0 && length(aws_ecs_service.app) == 0
    error_message = "EC2 구성에서는 EC2를 만들고 NAT·ALB·ECS는 만들지 않아야 합니다."
  }

  assert {
    condition     = length(aws_vpc_security_group_ingress_rule.db_from_ec2) == 1 && length(aws_vpc_security_group_ingress_rule.redis_from_ec2) == 1
    error_message = "EC2 앱 서버에서 RDS·Redis로 접속할 수 있어야 합니다."
  }

  assert {
    condition     = jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["SPRING_PROFILES_ACTIVE"] == "prod"
    error_message = "EC2 앱 환경변수에 prod 프로필이 있어야 합니다."
  }

  assert {
    condition = (
      jsondecode(aws_ssm_parameter.ec2_web_env[0].value)["SITE_ADDRESS"] == ":80" &&
      jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["JWT_COOKIE_SECURE"] == "false" &&
      jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["KAKAO_REDIRECT_URI"] == "http://203.0.113.10/oauth/kakao/callback" &&
      length(aws_route53_record.main) == 0
    )
    error_message = "도메인이 없으면 HTTP(:80)로 서비스하고 쿠키에 Secure를 붙이지 않아야 합니다."
  }

  assert {
    condition = (
      startswith(jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["DB_URL"], "jdbc:mysql://rds.mock:3306/ddang?") &&
      jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["REDIS_HOST"] == "redis.mock"
    )
    error_message = "EC2 앱이 RDS·ElastiCache 주소를 받아야 합니다."
  }
}

run "ec2_with_domain" {
  command = plan

  variables {
    ec2_enabled       = true
    ecs_enabled       = false
    domain_name       = "example.com"
    manage_dns_record = true
  }

  assert {
    condition = (
      jsondecode(aws_ssm_parameter.ec2_web_env[0].value)["SITE_ADDRESS"] == "example.com" &&
      jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["JWT_COOKIE_SECURE"] == "true"
    )
    error_message = "도메인이 있으면 Caddy가 그 도메인으로 HTTPS를 서비스하고 쿠키에 Secure를 붙여야 합니다."
  }

  assert {
    condition = (
      jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["KAKAO_REDIRECT_URI"] == "https://example.com/oauth/kakao/callback" &&
      jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["GOOGLE_REDIRECT_URI"] == "https://example.com/oauth/google/callback"
    )
    error_message = "소셜 로그인 redirect URI가 도메인의 콜백 주소여야 합니다."
  }

  assert {
    condition     = length(aws_route53_record.main) == 1 && length(aws_vpc_security_group_ingress_rule.ec2_https) == 2
    error_message = "도메인 A 레코드를 만들고 443(TCP·UDP)을 열어야 합니다."
  }
}

run "dns_moves_to_alb_before_ec2_off" {
  command = plan

  variables {
    ec2_enabled       = true
    ecs_enabled       = true
    domain_name       = "example.com"
    manage_dns_record = true
    dns_target        = "ecs"
  }

  assert {
    condition     = length(aws_route53_record.main[0].alias) == 1 && aws_route53_record.main[0].records == null
    error_message = "dns_target = ecs면 같은 A 레코드가 ALB 별칭을 가리켜야 합니다."
  }
}

run "dns_target_must_be_enabled" {
  command = plan

  variables {
    ec2_enabled       = false
    ecs_enabled       = true
    domain_name       = "example.com"
    manage_dns_record = true
    dns_target        = "ec2"
  }

  expect_failures = [aws_route53_record.main]
}

run "domain_must_be_bare" {
  command = plan

  variables {
    domain_name = "https://example.com"
  }

  expect_failures = [var.domain_name]
}

run "migrating" {
  command = plan

  variables {
    ec2_enabled = true
    ecs_enabled = true
  }

  assert {
    condition     = length(aws_instance.app) == 1 && length(aws_ecs_service.app) == 1 && length(aws_vpc_security_group_ingress_rule.db_from_ec2) == 1
    error_message = "전환 중에는 두 구성이 함께 있고, 둘 다 같은 RDS에 접속할 수 있어야 합니다."
  }
}

run "ecs_only" {
  command = plan

  variables {
    ec2_enabled = false
    ecs_enabled = true
  }

  assert {
    condition     = length(aws_instance.app) == 0 && length(aws_ecs_service.app) == 1 && length(aws_nat_gateway.main) == 1 && length(aws_vpc_security_group_ingress_rule.db_from_ec2) == 0
    error_message = "ECS 구성에서는 EC2 없이 ECS·NAT를 만들고, EC2용 DB 접속 규칙은 없어야 합니다."
  }
}
