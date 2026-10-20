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

  mock_resource "aws_elasticache_cluster" {
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
    condition     = length(aws_instance.app) == 1 && length(aws_nat_gateway.main) == 0 && length(aws_db_instance.main) == 0 && length(aws_lb.main) == 0
    error_message = "EC2 구성에서는 EC2만 만들고 NAT·RDS·ALB는 만들지 않아야 합니다."
  }

  assert {
    condition     = jsondecode(aws_ssm_parameter.ec2_app_env[0].value)["SPRING_PROFILES_ACTIVE"] == "prod"
    error_message = "EC2 앱 환경변수에 prod 프로필이 있어야 합니다."
  }
}

run "migrating" {
  command = plan

  variables {
    ec2_enabled = true
    ecs_enabled = true
  }

  assert {
    condition     = length(aws_instance.app) == 1 && length(aws_ecs_service.app) == 1 && length(aws_vpc_security_group_ingress_rule.db_from_ec2) == 1
    error_message = "전환 중에는 두 구성이 함께 있고, EC2에서 RDS로 접속할 수 있어야 합니다."
  }
}

run "ecs_only" {
  command = plan

  variables {
    ec2_enabled = false
    ecs_enabled = true
  }

  assert {
    condition     = length(aws_instance.app) == 0 && length(aws_ecs_service.app) == 1 && length(aws_nat_gateway.main) == 1 && length(aws_db_instance.main) == 1
    error_message = "ECS 구성에서는 EC2 없이 ECS·NAT·RDS를 만들어야 합니다."
  }
}
