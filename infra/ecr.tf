# 앱 이미지 저장소. EC2·ECS 구성이 같은 이미지를 쓴다.

resource "aws_ecr_repository" "app" {
  name                 = "${var.project}-app"
  image_tag_mutability = "MUTABLE" # 배포 워크플로가 커밋 SHA 태그와 latest 태그를 함께 올린다.

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 이미지 20개만 남긴다"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

# 두 구성이 공통으로 쓰는 앱 환경변수 (DB·Redis 주소는 구성마다 다르다)
locals {
  # LiveKit Cloud: 서버 API는 https://, 브라우저 접속은 wss:// 같은 호스트를 쓴다.
  livekit_api_url = replace(var.livekit_url, "wss://", "https://")

  common_app_environment = merge(var.app_environment, {
    SPRING_PROFILES_ACTIVE = "prod"
    DDL_AUTO               = var.ddl_auto
    LIVEKIT_API_URL        = local.livekit_api_url
    LIVEKIT_WS_URL         = var.livekit_url
  })
}
