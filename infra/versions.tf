terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # 상태 파일(tfstate)은 S3에 둔다. DB 비밀번호가 들어 있어 커밋하지 않고, PC를 바꾸거나 팀원이 apply해도 같은 상태를 쓴다.
  # 버킷은 Terraform이 만들지 않으므로 처음 한 번 직접 만든다. (docs/deploy.md "1. 인프라 만들기")
  # use_lockfile: 두 사람이 동시에 apply하지 못하게 S3에 잠금 파일을 둔다. (DynamoDB 불필요)
  # AWS 없이 테스트할 때는 `terraform init -backend=false` 후 `terraform test`
  backend "s3" {
    bucket       = "ddang-terraform-state-853900964851"
    key          = "ddang/prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}
