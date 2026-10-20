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

  # 상태 파일(tfstate)은 기본으로 이 폴더에 저장된다. 비밀번호가 들어 있으니 커밋하지 않는다. (.gitignore)
  # 팀원과 함께 관리하려면 S3 버킷을 만든 뒤 아래 주석을 풀고 `terraform init -migrate-state` 를 실행한다.
  # backend "s3" {
  #   bucket       = "<상태 파일용 S3 버킷 이름>"
  #   key          = "ddang/prod/terraform.tfstate"
  #   region       = "ap-northeast-2"
  #   use_lockfile = true
  # }
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
