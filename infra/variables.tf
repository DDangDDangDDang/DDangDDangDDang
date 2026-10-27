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
# RDS·ElastiCache는 두 구성이 함께 쓰므로 스위치와 관계없이 항상 만든다.
# 개발 중: ec2_enabled = true,  ecs_enabled = false  (EC2 앱 서버 + RDS + ElastiCache, 월 약 $65)
# 전환 중: ec2_enabled = true,  ecs_enabled = true   (둘 다 켜고 배포 대상만 바꾼다. docs/deploy.md "ECS로 전환")
# 운영:    ec2_enabled = false, ecs_enabled = true   (ALB + ECS + NAT + RDS + ElastiCache, 월 약 $160)

variable "ec2_enabled" {
  description = "EC2 구성(EC2 한 대에서 앱을 Docker Compose로 실행)을 만들지"
  type        = bool
  default     = true
}

variable "ecs_enabled" {
  description = "ECS 구성(ALB, Fargate, NAT)을 만들지"
  type        = bool
  default     = false
}

# ---------- EC2 구성 ----------

variable "ec2_instance_type" {
  description = "EC2 인스턴스 크기. 앱(Spring Boot)만 돌리지만 메모리 2GB 이상을 권장"
  type        = string
  default     = "t3.small"
}

variable "ec2_volume_size" {
  description = "EC2 디스크(GB). OS와 Docker 이미지·로그만 들어간다."
  type        = number
  default     = 20
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
    "WEB_PUSH_PUBLIC_KEY",
    "WEB_PUSH_PRIVATE_KEY",
    "KAKAO_CLIENT_ID",
    "KAKAO_CLIENT_SECRET",
    "GOOGLE_CLIENT_ID",
    "GOOGLE_CLIENT_SECRET",
  ]
}

# ---------- 도메인 ----------

variable "domain_name" {
  description = <<-EOT
    서비스 도메인 (예: ddangddangddang.com). 넣으면 EC2의 Caddy가 이 도메인으로 Let's Encrypt 인증서를 받아 HTTPS로 서비스하고,
    리프레시 토큰 쿠키에 Secure를 붙이고, 소셜 로그인 redirect URI를 https://<도메인>/oauth/{provider}/callback 으로 정한다.
    비워 두면 EC2는 HTTP(Elastic IP)로만 서비스한다.
  EOT
  type        = string
  default     = ""

  validation {
    condition     = var.domain_name == "" || can(regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$", var.domain_name))
    error_message = "domain_name은 https:// 나 경로 없이 도메인만 소문자로 적습니다. (예: ddangddangddang.com)"
  }
}

variable "manage_dns_record" {
  description = "Route 53 호스팅 영역(domain_name)에 EC2 고정 IP를 가리키는 A 레코드를 Terraform이 만들지. 도메인을 Route 53에서 샀으면 true, 다른 곳(가비아 등)이면 false로 두고 직접 등록한다."
  type        = bool
  default     = false
}

variable "dns_target" {
  description = "manage_dns_record = true일 때 도메인이 가리킬 구성. ec2(고정 IP) 또는 ecs(ALB). ECS로 전환할 때 EC2를 끄기 전에 ecs로 바꾼다."
  type        = string
  default     = "ec2"

  validation {
    condition     = contains(["ec2", "ecs"], var.dns_target)
    error_message = "dns_target은 ec2 또는 ecs여야 합니다."
  }
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

# ---------- DB · Redis (두 구성 공통) ----------

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

variable "github_oidc_subject_prefix" {
  description = <<-EOT
    GitHub OIDC 토큰 sub의 저장소 부분. 저장소가 "immutable subject"(소유자·저장소 ID 포함)를 쓰면 그 값을 넣는다.
    확인: gh api repos/<owner>/<repo>/actions/oidc/customization/sub 의 sub_claim_prefix
    비워 두면 예전 형식(repo:<owner>/<repo>)만 허용한다. 두 형식 모두 지정한 Environment에서 실행된 작업만 허용한다.
  EOT
  type        = string
  default     = "repo:DDangDDangDDang@339603727/DDangDDangDDang@1411657907"
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

# ---------- dev 사이트 (dev.<도메인>, infra/dev.tf) ----------

variable "dev_enabled" {
  description = "dev 사이트(EC2 한 대에 앱·웹·MySQL·Redis 컨테이너)를 만들지. dev 브랜치에 푸시하면 이 서버로 배포한다."
  type        = bool
  default     = false
}

variable "dev_instance_type" {
  description = "dev 서버 크기. 앱·MySQL·Redis를 한 대에 띄우므로 메모리 2GB 이상"
  type        = string
  default     = "t3.small"
}

variable "dev_livekit_url" {
  description = "dev 사이트가 쓸 LiveKit Cloud 프로젝트 주소. 비우면 운영과 같은 프로젝트(livekit_url)를 쓴다. (방 이름이 겹치지 않도록 따로 두는 것을 권장)"
  type        = string
  default     = ""

  validation {
    condition     = var.dev_livekit_url == "" || startswith(var.dev_livekit_url, "wss://")
    error_message = "dev_livekit_url은 wss:// 로 시작해야 합니다."
  }
}

variable "dev_app_environment" {
  description = "dev 사이트의 비밀이 아닌 추가 환경변수. 문자·메일은 기본으로 끄고 서버 로그로 확인한다."
  type        = map(string)
  default = {
    MAIL_ENABLED               = "false"
    SMS_ENABLED                = "false"
    CHAT_MODERATION_AI_ENABLED = "false"
  }
}

variable "github_dev_environment" {
  description = "dev 배포 워크플로가 쓰는 GitHub Environment 이름. 이 환경에서 실행된 작업만 dev 배포 역할을 받는다."
  type        = string
  default     = "development"
}
