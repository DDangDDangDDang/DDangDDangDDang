variable "project" {
  description = "리소스 이름 앞에 붙는 이름"
  type        = string
  default     = "ddang"
}

variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

# ---------- 배포 구성 선택 ----------
# 개발 중: ec2_enabled = true,  ecs_enabled = false  (EC2 한 대, 월 약 $27)
# 전환 중: ec2_enabled = true,  ecs_enabled = true   (둘 다 켜고 데이터를 옮긴다. docs/deploy.md "ECS로 전환")
# 운영:    ec2_enabled = false, ecs_enabled = true   (ALB + ECS + RDS + ElastiCache, 월 약 $160)

variable "ec2_enabled" {
  description = "EC2 한 대 구성(앱·MySQL·Redis를 Docker Compose로 실행)을 만들지"
  type        = bool
  default     = true
}

variable "ecs_enabled" {
  description = "ECS 구성(ALB, Fargate, RDS, ElastiCache, NAT)을 만들지"
  type        = bool
  default     = false
}

# ---------- EC2 한 대 구성 ----------

variable "ec2_instance_type" {
  description = "EC2 인스턴스 크기. 앱·MySQL·Redis를 함께 돌리므로 메모리 2GB 이상"
  type        = string
  default     = "t3.small"
}

variable "ec2_volume_size" {
  description = "EC2 디스크(GB). MySQL·Redis 데이터와 Docker 이미지가 들어간다."
  type        = number
  default     = 30
}

# ---------- 앱(ECS) ----------

variable "app_desired_count" {
  description = "실행할 서버(컨테이너) 수. 2대 이상이면 채팅은 Redis Pub/Sub으로 서버 간 전달된다."
  type        = number
  default     = 2
}

variable "app_cpu" {
  description = "컨테이너 1개의 CPU (1024 = 1 vCPU)"
  type        = number
  default     = 512
}

variable "app_memory" {
  description = "컨테이너 1개의 메모리(MiB)"
  type        = number
  default     = 1024
}

variable "ddl_auto" {
  description = "JPA ddl-auto (EC2·ECS 공통). 마이그레이션 도구(Flyway 등)를 도입하면 validate로 바꾼다."
  type        = string
  default     = "update"
}

variable "app_environment" {
  description = "비밀이 아닌 추가 환경변수 (EC2·ECS 공통. 예: MAIL_ENABLED, SMS_ENABLED, CHAT_MODERATION_AI_ENABLED)"
  type        = map(string)
  default = {
    MAIL_ENABLED               = "false"
    SMS_ENABLED                = "false"
    CHAT_MODERATION_AI_ENABLED = "false"
  }
}

variable "app_secret_keys" {
  description = <<-EOT
    Secrets Manager의 앱 비밀값(ddang/app) JSON에 반드시 있어야 하는 키.
    키가 하나라도 없으면 컨테이너가 시작되지 않는다. 쓰지 않는 기능의 키는 빈 문자열("")로 둔다.
  EOT
  type        = list(string)
  default = [
    "JWT_SECRET",
    "LIVEKIT_API_KEY",
    "LIVEKIT_API_SECRET",
    "PORTONE_API_SECRET",
    "PORTONE_WEBHOOK_SECRET",
    "MAIL_USERNAME",
    "MAIL_APP_PASSWORD",
    "SOLAPI_API_KEY",
    "SOLAPI_API_SECRET",
    "SOLAPI_SENDER",
    "ANTHROPIC_API_KEY",
  ]
}

# ---------- LiveKit Cloud ----------

variable "livekit_url" {
  description = "LiveKit Cloud 프로젝트 주소. 예: wss://ddang-xxxxxxxx.livekit.cloud"
  type        = string

  validation {
    condition     = startswith(var.livekit_url, "wss://")
    error_message = "livekit_url은 wss:// 로 시작해야 합니다."
  }
}

# ---------- DB · Redis ----------

variable "db_instance_class" {
  description = "RDS 인스턴스 크기"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "RDS 저장 용량(GB)"
  type        = number
  default     = 20
}

variable "db_deletion_protection" {
  description = "RDS 삭제 방지. terraform destroy 전에 false로 바꾸고 apply해야 지울 수 있다."
  type        = bool
  default     = true
}

variable "redis_node_type" {
  description = "ElastiCache 노드 크기"
  type        = string
  default     = "cache.t4g.micro"
}

# ---------- HTTPS (선택) ----------

variable "certificate_arn" {
  description = "ECS 구성의 ALB에 붙일 ACM 인증서 ARN. 비워 두면 HTTP(80)로만 서비스한다. 도메인을 연결하면 넣는다."
  type        = string
  default     = ""
}

# ---------- GitHub Actions 배포 ----------

variable "github_repository" {
  description = "배포 권한을 줄 GitHub 저장소 (owner/repo)"
  type        = string
  default     = "DDangDDangDDang/DDangDDangDDang"
}

variable "github_environment" {
  description = "배포 워크플로가 쓰는 GitHub Environment 이름. 이 환경에서 실행된 작업만 AWS 배포 권한을 받는다."
  type        = string
  default     = "production"
}

variable "create_github_oidc_provider" {
  description = "GitHub OIDC 공급자를 새로 만들지. 이 AWS 계정에 이미 있으면 false로 둔다."
  type        = bool
  default     = true
}
