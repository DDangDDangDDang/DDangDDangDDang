# 사용자가 올리는 파일(프로필 사진) 저장소. 앱이 APP_STORAGE_TYPE=s3로 이 버킷을 쓴다. (StorageProperties)
# - 브라우저가 앱이 만든 미리 서명된 주소로 버킷에 직접 올린다(PUT). 그래서 사이트 주소에서 오는 PUT을 CORS로 허용한다.
# - 화면은 버킷 주소(https://{bucket}.s3.{region}.amazonaws.com/{key})로 사진을 바로 보여 준다.
#   그래서 profile-images/(프로필 사진)·overlay-images/(방송 화면 이미지 박스) 아래만 누구나 읽을 수 있게 열고, 목록 보기·쓰기는 막아 둔다.
# - 운영과 dev는 버킷을 나눈다. dev 서버는 dev 버킷만, 운영 서버(EC2·ECS)는 운영 버킷만 쓸 수 있다.

locals {
  # 공개로 읽을 수 있는 키 앞부분. 앱의 UserService.profileImagePrefix(프로필 사진), OverlayBoxResolver.imagePrefix(방송 화면 이미지 박스)와 같아야 한다.
  public_upload_prefixes = ["profile-images/", "overlay-images/"]

  ec2_origin = local.ec2_https ? "https://${var.domain_name}" : "http://${local.ec2_public_ip}"

  upload_buckets = merge(
    {
      main = {
        name    = "${var.project}-uploads-${data.aws_caller_identity.current.account_id}"
        origins = compact([var.domain_name == "" ? "" : "https://${var.domain_name}", var.ec2_enabled ? local.ec2_origin : ""])
      }
    },
    var.dev_enabled ? {
      dev = {
        name    = "${var.project}-dev-uploads-${data.aws_caller_identity.current.account_id}"
        origins = [local.dev_origin]
      }
    } : {}
  )

  # 앱에 넘기는 저장소 환경변수 (ecr.tf의 공통 값, dev.tf의 dev 값에 합친다)
  main_storage_environment = {
    APP_STORAGE_TYPE      = "s3"
    APP_STORAGE_S3_BUCKET = local.upload_buckets["main"].name
    APP_STORAGE_S3_REGION = var.region
  }
  dev_storage_environment = var.dev_enabled ? {
    APP_STORAGE_TYPE      = "s3"
    APP_STORAGE_S3_BUCKET = local.upload_buckets["dev"].name
    APP_STORAGE_S3_REGION = var.region
  } : {}
}

resource "aws_s3_bucket" "uploads" {
  for_each = local.upload_buckets
  bucket   = each.value.name
}

# ACL은 쓰지 않고 버킷 정책으로만 권한을 정한다.
resource "aws_s3_bucket_ownership_controls" "uploads" {
  for_each = aws_s3_bucket.uploads
  bucket   = each.value.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# 공개 ACL은 막고, 아래 버킷 정책(public_upload_prefixes 읽기)만 공개를 허용한다.
resource "aws_s3_bucket_public_access_block" "uploads" {
  for_each = aws_s3_bucket.uploads
  bucket   = each.value.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

data "aws_iam_policy_document" "uploads_public_read" {
  for_each = aws_s3_bucket.uploads

  statement {
    sid       = "PublicReadProfileImages"
    actions   = ["s3:GetObject"]
    resources = [for prefix in local.public_upload_prefixes : "${each.value.arn}/${prefix}*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "uploads" {
  for_each = aws_s3_bucket.uploads
  bucket   = each.value.id
  policy   = data.aws_iam_policy_document.uploads_public_read[each.key].json

  # 공개 접근 차단 설정이 먼저 풀려야 공개 정책을 붙일 수 있다.
  depends_on = [aws_s3_bucket_public_access_block.uploads]
}

resource "aws_s3_bucket_cors_configuration" "uploads" {
  for_each = { for key, bucket in local.upload_buckets : key => bucket if length(bucket.origins) > 0 }
  bucket   = aws_s3_bucket.uploads[each.key].id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = each.value.origins
    allowed_headers = ["*"]
    max_age_seconds = 3000
  }
}

# 앱이 쓰는 권한: 올릴 주소 만들기(PutObject), 올라왔는지 확인(GetObject = HeadObject), 바꾼 사진 지우기(DeleteObject)
data "aws_iam_policy_document" "uploads_app" {
  for_each = aws_s3_bucket.uploads

  statement {
    actions   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
    resources = ["${each.value.arn}/*"]
  }
}

resource "aws_iam_role_policy" "ec2_uploads" {
  count  = var.ec2_enabled ? 1 : 0
  name   = "app-uploads"
  role   = aws_iam_role.ec2[0].id
  policy = data.aws_iam_policy_document.uploads_app["main"].json
}

resource "aws_iam_role_policy" "ecs_uploads" {
  count  = var.ecs_enabled ? 1 : 0
  name   = "app-uploads"
  role   = aws_iam_role.ecs_task[0].id
  policy = data.aws_iam_policy_document.uploads_app["main"].json
}

resource "aws_iam_role_policy" "dev_uploads" {
  count  = var.dev_enabled ? 1 : 0
  name   = "app-uploads"
  role   = aws_iam_role.dev[0].id
  policy = data.aws_iam_policy_document.uploads_app["dev"].json
}
