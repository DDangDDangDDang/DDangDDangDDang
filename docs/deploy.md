# AWS 배포 가이드

main 브랜치에 머지되면 GitHub Actions가 테스트 → 이미지 빌드 → 배포까지 자동으로 합니다.
배포 구성은 두 가지이고, 같은 Terraform 코드(`infra/`)에서 스위치로 고릅니다.

| | EC2 (개발 중, 지금) | ECS (운영) |
|---|---|---|
| 스위치 | `ec2_enabled = true`, `ecs_enabled = false` | `ec2_enabled = false`, `ecs_enabled = true` |
| 앱 | EC2 1대에서 Docker Compose로 실행 (Caddy가 화면·HTTPS 담당) | ALB + ECS Fargate 2대 + NAT (API만, 화면 제공은 전환 때 추가) |
| DB · Redis | RDS MySQL + ElastiCache Redis (두 구성 공통) | 같음 |
| 월 비용(대략) | **약 $65** | 약 $160 |
| 배포 중 | 약 30초 끊김 | 끊김 없음 (롤링 배포) |
| 실패한 배포 | 서버 스크립트가 이전 이미지로 되돌림 | ECS가 이전 버전으로 되돌림 |
| DB 백업 | RDS 자동 백업 7일 | 같음 |
| 서버 장애 | 앱이 멈춤 (데이터는 RDS에 그대로) | 다른 서버가 계속 처리 |

두 구성은 **같은 Docker 이미지, 같은 비밀값(Secrets Manager), 같은 RDS·ElastiCache, 같은 배포 역할**을 씁니다.
그래서 ECS로 바꿀 때 앱 코드·설정은 고치지 않고 DB도 옮기지 않습니다. 인프라 스위치와 배포 대상 변수만 바꾸면 됩니다. (아래 "ECS로 전환")

NAT 게이트웨이(월 약 $43)와 ALB(월 약 $20)는 ECS 구성에서만 만듭니다.
EC2 구성에서는 앱 서버가 퍼블릭 서브넷에 있어 NAT 없이 외부 API를 호출하고, RDS·Redis는 프라이빗 서브넷에서 밖으로 나갈 일이 없습니다.

```
[EC2 구성]
브라우저 ──HTTPS:443──▶ EC2 (Elastic IP, 퍼블릭 서브넷)  ※ ddangddangddang.com A 레코드
                        └ Docker Compose
                           web(Caddy): 인증서 자동 발급, 화면(정적 파일), /api·/ws ─▶ app(8080)
                                                                                  ├─▶ RDS MySQL 8.4        (프라이빗 서브넷)
                                                                                  ├─▶ ElastiCache Redis 7  (프라이빗 서브넷)
                                                                                  └─▶ 외부 API (인터넷 게이트웨이)
GitHub Actions ──OIDC──▶ ECR에 앱·웹 이미지 푸시 ──SSM Run Command──▶ EC2에서 deploy.sh 실행

[ECS 구성]
브라우저 ──HTTP(S)/WebSocket──▶ ALB ──▶ ECS Fargate (컨테이너 2개, 프라이빗 서브넷)
                                         ├─▶ RDS MySQL 8.4
                                         ├─▶ ElastiCache Redis 7
                                         └─▶ NAT ─▶ 외부 API (LiveKit Cloud, 포트원, SOLAPI, Gmail, Anthropic)
GitHub Actions ──OIDC──▶ ECR에 이미지 푸시 ──▶ ECS 새 작업 정의·롤링 배포
```

| 파일 | 역할 |
|---|---|
| `Dockerfile` | jar 빌드 후 JRE 이미지로 실행 (두 구성 공통) |
| `frontend/Dockerfile` | 웹 이미지: 프론트엔드 빌드 + Caddy (EC2 구성) |
| `infra/ec2/Caddyfile` | HTTPS 자동 발급, `/api`·`/ws`를 앱으로, 나머지는 화면 |
| `src/main/resources/application-prod.yml` | 운영 설정 (비밀값 기본값 없음, 프록시 헤더, SQL 로그 끔) |
| `infra/*.tf` | AWS 리소스 전체. `ec2.tf`는 EC2 구성, `ecs.tf`·`alb.tf`는 ECS 구성, `data_stores.tf`(RDS·ElastiCache)는 공통 |
| `infra/ec2/` | EC2 서버의 `docker-compose.yml`(web + app), `deploy.sh`, 첫 부팅 스크립트 |
| `infra/tests/` | 세 가지 구성이 plan까지 되는지 확인하는 테스트 (`terraform test`) |
| `.github/workflows/deploy.yml` | main 푸시 시 테스트 → 이미지 빌드 → `DEPLOY_TARGET`에 따라 EC2 또는 ECS 배포 |

## 비용

**EC2 구성 (지금)**

| 항목 | 월 비용 |
|---|---|
| EC2 t3.small (2 vCPU, 2GB) | 약 $19 |
| EC2 디스크 20GB (gp3) | 약 $2 |
| 공인 IPv4 (Elastic IP) | 약 $3.6 |
| RDS MySQL db.t4g.micro + 20GB + 백업 | 약 $22 |
| ElastiCache Redis cache.t4g.micro | 약 $18 |
| Secrets Manager, ECR, S3(상태 파일), Route 53 호스팅 영역 | 2~3달러 |

합계 약 **$65 / 월**. NAT·ALB가 없어 ECS 구성보다 고정비가 작습니다.
(AWS 프리 티어 대상 계정이면 처음 12개월은 EC2·RDS·ElastiCache 일부가 무료일 수 있습니다. 인스턴스 크기가 프리 티어 대상인지 확인하세요.)

**ECS 구성 (운영)**: NAT 약 $43, Fargate 2대 약 $41, RDS 약 $22, ALB 약 $20, ElastiCache 약 $18, 공인 IPv4 약 $11 등 합계 약 **$160 / 월**.

모두 트래픽이 적을 때의 어림값입니다. 정확한 값은 [AWS 요금 계산기](https://calculator.aws/)로 확인하세요.

## 0. 준비물

- AWS 계정, 그리고 관리자 권한이 있는 IAM 사용자(또는 IAM Identity Center) 로그인
  - 루트 계정으로 작업하지 마세요.
- [AWS CLI](https://aws.amazon.com/cli/) v2: `aws configure` 또는 `aws configure sso`로 로그인
- [Terraform](https://developer.hashicorp.com/terraform/install) 1.10 이상
  - 설치하지 않고 Docker로 실행해도 됩니다: `docker run --rm -it -v "$PWD:/w" -w /w -v "$HOME/.aws:/root/.aws:ro" hashicorp/terraform:1.13 <명령>` (Git Bash는 앞에 `MSYS_NO_PATHCONV=1`)
- [LiveKit Cloud](https://cloud.livekit.io) 프로젝트 1개 (무료 플랜 가능): Project URL, API Key, API Secret
- 도메인 (`ddangddangddang.com`). HTTPS는 카메라·마이크(판매자 송출), 웹 푸시, 구글 로그인, Secure 쿠키에 꼭 필요합니다.
- 카카오·구글 로그인 앱: redirect URI `https://<도메인>/oauth/kakao/callback`, `https://<도메인>/oauth/google/callback`
- SOLAPI 발신번호 등록 (회원가입 문자 인증)
- 서버 접속용 [Session Manager 플러그인](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) (선택)

```bash
aws sts get-caller-identity   # 로그인된 계정 확인
terraform -version
```

## 1. 인프라 만들기 (EC2 구성)

**처음 한 번: 상태 파일용 S3 버킷 만들기.** Terraform 상태(DB 비밀번호 포함)는 S3에 둡니다. (`infra/versions.tf`)
버킷 이름은 계정 ID를 붙여 전 세계에서 겹치지 않게 했습니다. 다른 계정에서 쓰면 `versions.tf`의 `bucket`도 바꿉니다.

```bash
BUCKET=ddang-terraform-state-853900964851
aws s3api create-bucket --bucket "$BUCKET" --region ap-northeast-2 \
  --create-bucket-configuration LocationConstraint=ap-northeast-2
aws s3api put-bucket-versioning --bucket "$BUCKET" --versioning-configuration Status=Enabled   # 실수로 덮어써도 이전 상태로 되돌린다
aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

> **이미 로컬 상태로 apply한 적이 있다면** (예전 버전 문서대로 `infra/terraform.tfstate`가 있는 경우)
> 새로 checkout한 폴더가 아니라 **그 상태 파일이 있는 원래 폴더**에서 진행합니다.
> 1. `cp terraform.tfstate ~/terraform.tfstate.backup` 으로 백업
> 2. `terraform init -migrate-state` → 기존 상태를 S3로 복사할지 물으면 `yes`
> 3. `aws s3 ls s3://ddang-terraform-state-853900964851/ddang/prod/` 에 `terraform.tfstate`가 보이는지 확인
> 4. `terraform plan`이 "No changes"(또는 이 문서에서 의도한 변경만)인지 확인한 뒤 apply. 처음부터 다 새로 만든다고 나오면 멈추고 백업을 확인합니다.
>
> 처음 만드는 경우에는 아래처럼 `terraform init`만 하면 됩니다.

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # livekit_url, domain_name, app_environment 채우기
terraform init
terraform plan     # 만들 리소스 확인
terraform apply    # 약 10~15분 (RDS·ElastiCache 생성이 가장 오래 걸린다)
```

EC2는 켜진 뒤 1~2분 동안 Docker를 설치합니다. 앱은 4단계의 첫 배포 때 뜹니다.
DB 테이블은 첫 배포 때 앱이 만듭니다(`ddl-auto=update`).

**도메인 연결**: `manage_dns_record = true`(Route 53에서 산 도메인)면 apply가 `도메인 → EC2 고정 IP` A 레코드를 만듭니다.
다른 곳에서 샀으면 그곳 DNS 관리에서 A 레코드(`@` → `terraform output -raw ec2_public_ip`)를 직접 넣습니다.
DNS가 연결된 뒤 첫 배포를 하면 Caddy가 Let's Encrypt 인증서를 자동으로 받습니다. (DNS가 늦게 퍼져도 Caddy가 알아서 다시 시도합니다)

## 2. 앱 비밀값 넣기 (Secrets Manager)

`ddang/app` 시크릿에 아래 키를 **모두** 넣습니다. 쓰지 않는 기능(메일, 문자, AI 검토 등)의 키는 빈 문자열(`""`)로 둡니다.
(ECS 구성은 키가 하나라도 없으면 컨테이너가 시작되지 않습니다. 두 구성 모두 같은 키를 씁니다.)

| 키 | 값 | 필수 |
|---|---|---|
| `JWT_SECRET` | Base64 256비트 이상 키. `openssl rand -base64 48` | ✅ |
| `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` | LiveKit Cloud → Settings → Keys | ✅ |
| `PORTONE_API_SECRET` / `PORTONE_WEBHOOK_SECRET` | 포트원 콘솔 | 결제 사용 시 |
| `MAIL_USERNAME` / `MAIL_APP_PASSWORD` | Gmail 주소 / 앱 비밀번호 | `MAIL_ENABLED=true`일 때 |
| `SOLAPI_API_KEY` / `SOLAPI_API_SECRET` / `SOLAPI_SENDER` | SOLAPI 콘솔 / 등록한 발신번호 | `SMS_ENABLED=true`일 때 |
| `ANTHROPIC_API_KEY` | Claude API 키 | `CHAT_MODERATION_AI_ENABLED=true`일 때 |
| `WEB_PUSH_PUBLIC_KEY` / `WEB_PUSH_PRIVATE_KEY` | 브라우저 알림(웹 푸시) VAPID 키. `npx web-push generate-vapid-keys`의 Public Key / Private Key. 한 번 정하면 바꾸지 않음 (바꾸면 모든 회원이 다시 구독해야 함) | ✅ |
| `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` | 카카오디벨로퍼스 → [앱] → [플랫폼 키] → REST API 키 / 그 키의 클라이언트 시크릿 | 카카오 로그인 사용 시 |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google Cloud 콘솔 → OAuth 클라이언트(웹 애플리케이션) | 구글 로그인 사용 시 |

소셜 로그인 redirect URI는 비밀값이 아니라 `domain_name`으로 Terraform이 정합니다. (`https://<도메인>/oauth/{kakao|google}/callback`)

값에는 작은따옴표(`'`)와 줄바꿈을 넣을 수 없습니다. (EC2 배포 스크립트가 거부합니다)

**AWS 콘솔**: Secrets Manager → `ddang/app` → 보안 암호 값 검색 → 편집 → 일반 텍스트에 JSON을 붙여넣기

```json
{
  "JWT_SECRET": "<openssl rand -base64 48 결과>",
  "LIVEKIT_API_KEY": "<키>",
  "LIVEKIT_API_SECRET": "<시크릿>",
  "PORTONE_API_SECRET": "",
  "PORTONE_WEBHOOK_SECRET": "",
  "MAIL_USERNAME": "",
  "MAIL_APP_PASSWORD": "",
  "SOLAPI_API_KEY": "",
  "SOLAPI_API_SECRET": "",
  "SOLAPI_SENDER": "",
  "ANTHROPIC_API_KEY": "",
  "WEB_PUSH_PUBLIC_KEY": "<Public Key>",
  "WEB_PUSH_PRIVATE_KEY": "<Private Key>",
  "KAKAO_CLIENT_ID": "",
  "KAKAO_CLIENT_SECRET": "",
  "GOOGLE_CLIENT_ID": "",
  "GOOGLE_CLIENT_SECRET": ""
}
```

또는 CLI로 넣을 수 있습니다. JSON을 파일로 저장한 뒤 실행하고, 실행 후 파일은 지우세요.

```bash
aws secretsmanager put-secret-value --secret-id ddang/app --secret-string file://app-secrets.json
```

메일·문자·AI 검토 같은 기능 켜기/끄기는 비밀값이 아니라 `infra/terraform.tfvars`의 `app_environment`로 바꿉니다.

## 3. GitHub 설정

1. 저장소 → **Settings → Environments → New environment** → 이름 `production`
2. **Deployment branches and tags**를 **Selected branches and tags**로 바꾸고 `main`만 추가합니다.
   - 다른 브랜치에서는 배포 역할을 쓸 수 없게 됩니다. (AWS 쪽에서도 `environment:production`만 허용함)
3. 같은 화면 **Environment variables**에 `terraform output github_variables` 값을 넣습니다.

| 이름 | 예시 | 구성 |
|---|---|---|
| `AWS_REGION` | `ap-northeast-2` | 공통 |
| `AWS_DEPLOY_ROLE_ARN` | `arn:aws:iam::<계정 ID>:role/ddang-github-deploy` | 공통 |
| `ECR_REPOSITORY` | `ddang-app` | 공통 |
| `EC2_INSTANCE_ID` | `i-0123...` | EC2 |
| `ECR_WEB_REPOSITORY` | `ddang-web` | EC2 |
| `ECS_CLUSTER`, `ECS_SERVICE`, `ECS_TASK_FAMILY`, `ECS_CONTAINER_NAME` | `ddang`, `ddang-app`, `ddang-app`, `app` | ECS (전환할 때 추가) |

   같은 곳에 **화면 빌드용 공개 값**도 넣습니다. 브라우저에 그대로 보이는 값이라 Secrets가 아니라 Variables입니다. 없는 값은 비워 두면 해당 버튼에서 "설정이 필요합니다"라고 안내합니다.

| 이름 | 값 |
|---|---|
| `VITE_KAKAO_REST_API_KEY` | 카카오 REST API 키 (`KAKAO_CLIENT_ID`와 같은 값) |
| `VITE_GOOGLE_CLIENT_ID` | 구글 클라이언트 ID (`GOOGLE_CLIENT_ID`와 같은 값) |
| `VITE_PORTONE_STORE_ID` / `VITE_PORTONE_CHANNEL_KEY` | 포트원 콘솔의 상점 ID / 채널 키 (결제 사용 시) |

4. **배포 대상**은 Environment가 아니라 **저장소 변수**로 넣습니다.
   **Settings → Secrets and variables → Actions → Variables → New repository variable**
   - 이름 `DEPLOY_TARGET`, 값 `ec2`
   - (워크플로가 어느 배포 작업을 실행할지 Environment에 들어가기 전에 정하기 때문입니다.)
   - 이 변수가 없으면 main에 머지돼도 테스트만 하고 빌드·배포는 건너뜁니다. AWS를 만들기 전에는 비워 두세요.

AWS 액세스 키는 GitHub에 넣지 않습니다. 배포는 OIDC로 역할을 잠깐 빌려서 합니다.

## 4. 첫 배포

`main`에 머지하면 자동으로 배포됩니다. 처음에는 **Actions → Deploy → Run workflow**(branch: `main`)로 직접 실행해도 됩니다.

진행 순서: 배포 대상 확인 → 테스트 → 앱·웹 이미지 빌드·ECR 푸시 → EC2 배포
- EC2 배포는 SSM으로 서버의 `deploy.sh`를 실행합니다. 새 이미지로 바꾼 뒤 3분 안에 앱 헬스 체크가 통과하고 웹(Caddy) 컨테이너가 떠 있지 않으면 이전 이미지로 되돌리고 워크플로를 실패로 표시합니다.
- 인증서 발급은 배포 성공 여부에 넣지 않습니다. HTTPS가 안 되면 아래 "5. 확인"의 web 로그를 봅니다.
- 서버 출력(앱 로그 일부 포함)은 워크플로의 "서버에서 배포 스크립트 실행" 단계에서 볼 수 있습니다.

## 5. 확인

```bash
cd infra
terraform output -raw ec2_url                       # https://ddangddangddang.com
curl -I "$(terraform output -raw ec2_url)"          # 200, 화면(index.html)
curl -I "http://ddangddangddang.com"                # 308 → https 로 이동
```

`/actuator`는 밖에 열어 두지 않았습니다. 앱 상태는 서버 안에서 `curl http://127.0.0.1:8080/actuator/health/liveness`로 봅니다.
HTTPS가 안 되면 서버에서 `docker compose logs web`을 봅니다. 대부분 DNS가 아직 EC2 IP를 가리키지 않거나 80 포트가 막힌 경우입니다.

서버 접속 (SSH 대신 Session Manager):

```bash
aws ssm start-session --target "$(terraform output -raw ec2_instance_id)"
sudo -i
cd /opt/ddang
docker compose ps
docker compose logs -f app      # 웹(Caddy)은 web
```

## dev 사이트 (dev.ddangddangddang.com)

**dev 브랜치에 푸시(머지)하면** 테스트 후 dev 사이트로 자동 배포됩니다. 운영(main → ddangddangddang.com)과 같은 워크플로(`deploy.yml`)를 쓰지만, 아래가 모두 따로라 dev 배포가 운영에 닿지 않습니다.

| | 운영 | dev |
|---|---|---|
| 서버 | EC2 `ddang-app` | EC2 `ddang-dev` (`infra/dev.tf`) |
| DB · Redis | RDS · ElastiCache | 같은 서버의 MySQL 8.4 · Redis 7.4 컨테이너 (`infra/ec2/docker-compose.dev.yml`) |
| 이미지 저장소 | `ddang-app`, `ddang-web` | `ddang-dev-app`, `ddang-dev-web` |
| 비밀값 | `ddang/app`, `ddang/db` | `ddang-dev/app`, `ddang-dev/db` |
| GitHub Environment / AWS 역할 | `production` / `ddang-github-deploy` | `development` / `ddang-github-deploy-dev` (dev 서버에만 배포 가능) |
| 문자·메일 | 실제 발송 | 기본 꺼짐. 인증번호는 dev 서버 앱 로그에서 확인 |
| 월 비용 | 약 $65 | 약 $20 (t3.small + 디스크 + 고정 IP). 꺼 두면 약 $6 |

dev 데이터는 서버 디스크(Docker 볼륨)에만 있습니다. 배포해도 남지만 서버를 다시 만들면 사라집니다.

**켜기**

1. `infra/terraform.tfvars`에 `dev_enabled = true` (LiveKit은 dev 프로젝트를 따로 만들어 `dev_livekit_url`에 넣는 것을 권장. 비우면 운영 프로젝트를 같이 씀) → `terraform apply`
2. Secrets Manager `ddang-dev/app`에 "2. 앱 비밀값 넣기"와 같은 키를 넣습니다. **`JWT_SECRET`은 운영과 다른 새 값**으로 만듭니다. (같으면 dev 토큰으로 운영에 로그인할 수 있음)
3. 카카오·구글 콘솔에 redirect URI `https://dev.ddangddangddang.com/oauth/kakao/callback`, `.../oauth/google/callback` 추가
4. GitHub → Settings → Environments → **New environment** `development`
   - Deployment branches and tags: **Selected branches** → `dev`
   - Variables: `terraform output github_variables_dev`의 5개 + `VITE_KAKAO_REST_API_KEY`, `VITE_GOOGLE_CLIENT_ID`
5. 저장소 변수(Settings → Secrets and variables → Actions → Variables) `DEV_DEPLOY_ENABLED` = `true`
6. Actions → Deploy → Run workflow (branch: `dev`)로 첫 배포. 이후로는 dev에 머지할 때마다 자동

**자주 하는 일**

| 하고 싶은 일 | 방법 |
|---|---|
| 서버 접속 | `aws ssm start-session --target "$(terraform output -json github_variables_dev \| jq -r .EC2_INSTANCE_ID)"` → `sudo -i; cd /opt/ddang` |
| 인증번호 확인 (문자·메일 꺼짐) | 서버에서 `docker compose logs app \| grep -i 인증` |
| dev DB 접속 | 서버에서 `docker compose exec mysql sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" ddang'` |
| dev 데이터 초기화 | 서버에서 `docker compose down -v` 후 Actions → Deploy → Run workflow (branch: `dev`) |
| 잠시 끄기 (비용 절약) | `aws ec2 stop-instances --instance-ids <ID>` / 다시 켜기 `start-instances`. 켜면 컨테이너가 자동으로 다시 뜸 |
| 완전히 없애기 | `dev_enabled = false` → `terraform apply`, GitHub 변수 `DEV_DEPLOY_ENABLED` 삭제 |

## 운영 중 자주 하는 일 (EC2 구성)

| 하고 싶은 일 | 방법 |
|---|---|
| 새 버전 배포 | main에 머지 (자동) |
| 이전 버전으로 되돌리기 | 되돌릴 커밋을 revert해서 main에 머지 |
| 비밀값 변경 | Secrets Manager 수정 → Actions → Deploy → Run workflow |
| 환경변수 변경 | `infra/` 수정 → `terraform apply` → Actions → Deploy → Run workflow |
| `docker-compose.yml`·`deploy.sh` 변경 | `infra/ec2/` 수정 → `terraform apply` → Actions → Deploy → Run workflow |
| DB 직접 접속 | 서버 접속 후 `docker run --rm -it mysql:8.4 mysql -h <terraform output -raw rds_address 값> -u ddang -p` (비밀번호는 Secrets Manager `ddang/db`) |
| DB 백업에서 복구 | 아래 "DB 백업에서 복구" 참고 |
| 메모리 부족(앱이 자꾸 재시작) | `ec2_instance_type = "t3.medium"`(4GB, 약 $38) → `terraform apply` |
| 서버를 새로 만들기 | `terraform apply -replace='aws_instance.app[0]'` → Actions → Deploy → Run workflow (데이터는 RDS에 있어 잃지 않음) |

서버 파일(`/opt/ddang`)은 배포 때마다 Terraform이 올린 값으로 덮어씁니다. 서버에서 직접 고치지 마세요.

### DB 백업에서 복구

RDS의 특정 시점 복원은 **새 인스턴스**를 만듭니다. 앱의 `DB_URL`은 Terraform이 관리하는 `ddang-mysql` 주소로 정해지므로,
복원한 인스턴스를 앱에 바로 붙이지 않고 **임시 인스턴스로 복원 → 데이터를 `ddang-mysql`로 복사 → 임시 인스턴스 삭제** 순서로 합니다.
Terraform 상태와 앱 설정은 바꾸지 않습니다. 복사하는 동안 앱을 멈추므로 점검 시간을 잡으세요.

1. RDS 콘솔 → `ddang-mysql` → 작업 → **특정 시점으로 복원**
   - DB 인스턴스 식별자: `ddang-mysql-restore`
   - 서브넷 그룹 `ddang-db`, VPC 보안 그룹 `ddang-db`(기본 그룹은 빼기), 퍼블릭 액세스 **아니요**
   - 상태가 "사용 가능"이 될 때까지 기다립니다. (10~20분)
2. **ECS 구성도 켜져 있으면(전환 중)** 같은 RDS에 쓰는 ECS 앱을 먼저 멈춥니다. EC2 구성만 쓰고 있으면 건너뜁니다.

```bash
aws ecs update-service --cluster ddang --service ddang-app --desired-count 0
aws ecs wait services-stable --cluster ddang --services ddang-app
```

3. 서버에 접속해 앱을 멈추고, 복원본에서 `ddang-mysql`로 데이터를 덮어씁니다.
   복사가 성공하면 Redis의 앱 캐시(카테고리 목록, 도네이션 포인트 정책)만 지운 뒤 앱을 켭니다. 복원 전 값이 최대 10분간 남지 않게 하기 위해서이고, 로그인 정보 등 다른 Redis 데이터는 그대로 둡니다.

```bash
aws ssm start-session --target "$(terraform output -raw ec2_instance_id)"
sudo -i
cd /opt/ddang
source <(grep -E '^(DB_(URL|USERNAME|PASSWORD)|REDIS_HOST)=' app.env | sed "s/^/export /")
MAIN="$(sed -E 's|jdbc:mysql://([^:/]+).*|\1|' <<<"$DB_URL")"
RESTORE=<RDS 콘솔의 ddang-mysql-restore 엔드포인트>
set -o pipefail                               # mysqldump가 실패해도 성공으로 보이지 않게 한다
if ! docker compose stop app; then            # 앱을 멈춰 쓰기를 막는다
  echo "앱을 멈추지 못했습니다. 복구를 중단합니다."
elif docker run --rm -e MYSQL_PWD="$DB_PASSWORD" mysql:8.4 mysqldump --ssl-mode=REQUIRED -h "$RESTORE" -u"$DB_USERNAME" \
       --single-transaction --routines --triggers --set-gtid-purged=OFF --databases ddang --add-drop-database \
     | docker run --rm -i -e MYSQL_PWD="$DB_PASSWORD" mysql:8.4 mysql --ssl-mode=REQUIRED -h "$MAIN" -u"$DB_USERNAME"; then
  # 앱 캐시만 지운다 (Spring 캐시 키: <캐시 이름>::<키>)
  docker run --rm redis:7.4 sh -c 'for p in "categories::*" "donationPolicy::*"; do
    redis-cli -h "$0" --scan --pattern "$p" | while IFS= read -r k; do redis-cli -h "$0" del "$k" >/dev/null; done; done' "$REDIS_HOST"
  docker compose start app
else
  echo "복구 실패: 앱은 멈춘 상태로 둡니다."
fi
```

`--databases ddang --add-drop-database`라 `ddang-mysql`의 `ddang` 스키마를 **통째로 지우고** 복원본으로 다시 만듭니다. 복원 시점 이후에 생긴 테이블도 남지 않습니다.
복사가 실패하면 앱을 켜지 않습니다. 스키마가 비었거나 중간까지만 채워졌을 수 있으니, 원인을 고친 뒤 위 명령을 다시 실행하세요.
미리 찍어 둔 수동 스냅샷으로 되돌릴 때도 스냅샷 복원은 `ddang-mysql`을 덮어쓰지 않고 새 인스턴스를 만듭니다.
그 스냅샷을 `ddang-mysql-restore`로 복원한 뒤(1번과 같은 설정) 같은 명령으로 복사하면, 복사가 성공했을 때만 앱이 켜집니다.
두 명령 모두 `--ssl-mode=REQUIRED`로 TLS 연결만 씁니다.

4. 2번에서 ECS를 멈췄다면 다시 켭니다: `aws ecs update-service --cluster ddang --service ddang-app --desired-count 2` (`app_desired_count` 값)

5. 앱이 정상인지 확인한 뒤 RDS 콘솔에서 `ddang-mysql-restore`를 삭제합니다. (최종 스냅샷 없이 삭제해도 됩니다. 남겨 두면 계속 비용이 듭니다)

덮어쓰기 전에 RDS 콘솔에서 `ddang-mysql`의 수동 스냅샷을 찍어 두면, 잘못된 시점으로 복원했을 때 되돌릴 수 있습니다.

## ECS로 전환 (개발 완료 후)

두 구성이 같은 RDS·ElastiCache를 쓰므로 **데이터를 옮기지 않습니다.**
ECS를 잠시 함께 띄우고, 배포 대상을 바꾼 뒤, 마지막에 EC2를 끕니다. 점검 시간도 따로 필요 없습니다.

**1) ECS 구성 켜기** (EC2는 계속 서비스 중)

```hcl
# infra/terraform.tfvars
ec2_enabled = true
ecs_enabled = true
```

```bash
terraform apply   # 약 5~10분 (ALB·NAT·ECS 생성)
```

**2) GitHub에 ECS 변수 추가**: `terraform output github_variables`의 `ECS_*` 4개를 production Environment에 추가합니다. `DEPLOY_TARGET`은 아직 `ec2`로 둡니다.

**3) 배포 대상 바꾸기**: 저장소 변수 `DEPLOY_TARGET`을 `ecs`로 바꾸고 Actions → Deploy → Run workflow.
배포가 끝나면 `terraform output app_url`(ALB 주소)로 접속해 확인합니다.
같은 DB·Redis를 쓰므로 회원 데이터와 로그인 상태가 그대로 이어집니다.

**4) 도메인을 ALB로 옮기기** (EC2를 끄기 전에)

EC2를 먼저 끄면 도메인이 가리키는 곳이 없어 접속이 끊깁니다. 위 "도메인·HTTPS"의 ECS 인증서(`certificate_arn`)를 먼저 붙인 뒤 옮깁니다.

- `manage_dns_record = true`면 `dns_target = "ecs"`로 바꾸고 `terraform apply`. 같은 A 레코드를 제자리에서 ALB 별칭으로 바꿉니다.
- 다른 곳에서 산 도메인이면 그곳 DNS를 `alb_dns_name`으로 바꿉니다. ALB는 IP가 바뀌므로 A 레코드에 IP를 넣을 수 없고,
  루트 도메인(`ddangddangddang.com`, `@`)에는 일반 CNAME도 쓸 수 없습니다.
  - DNS 업체가 ALIAS·ANAME(또는 CNAME 플래트닝)을 지원하면 `@`를 그 방식으로 `alb_dns_name`에 연결합니다.
  - 지원하지 않으면 네임서버를 Route 53으로 옮기는 것이 가장 간단합니다: Route 53에 호스팅 영역을 만들고 → 도메인 업체의 네임서버를 그 영역의 NS 4개로 바꾼 뒤 → `manage_dns_record = true`, `dns_target = "ecs"`로 apply.
  - `www.` 같은 하위 도메인은 CNAME으로 `alb_dns_name`을 가리킬 수 있지만, 서비스 주소가 바뀌므로 `domain_name`과 카카오·구글 redirect URI도 함께 바꿔야 합니다.
- DNS가 퍼질 때까지(TTL 5분) 기다린 뒤 다음 단계로 갑니다.

**5) EC2 끄기**

```hcl
ec2_enabled = false
ecs_enabled = true
```

```bash
terraform apply   # EC2, Elastic IP 삭제. RDS·ElastiCache는 그대로
```

GitHub의 `EC2_INSTANCE_ID`, `ECR_WEB_REPOSITORY` 변수는 지워도 됩니다.
(`dns_target = "ec2"`인 채로 EC2를 끄려고 하면 Terraform이 plan 단계에서 막습니다)

## 운영 중 자주 하는 일 (ECS 구성)

| 하고 싶은 일 | 방법 |
|---|---|
| 새 버전 배포 | main에 머지 (자동, 끊김 없음) |
| 이전 버전으로 되돌리기 | 되돌릴 커밋을 revert해서 main에 머지. 급하면 ECS 콘솔에서 서비스 업데이트 → 이전 작업 정의 리비전 선택 |
| 비밀값 변경 | Secrets Manager 수정 후 Actions → Deploy → Run workflow (컨테이너가 새로 떠야 반영됨) |
| 환경변수·CPU·메모리 변경 | `infra/` 수정 → `terraform apply` → Actions → Deploy → Run workflow |
| 서버 수 변경 | `app_desired_count` 수정 → `terraform apply` |
| 로그 | CloudWatch → 로그 그룹 `/ecs/ddang-app` |
| 컨테이너 접속 | `aws ecs execute-command --cluster ddang --task <작업 ID> --container app --interactive --command "/bin/sh"` |

## 도메인·HTTPS

- **EC2 구성**: `domain_name`을 넣으면 Caddy가 Let's Encrypt 인증서를 자동으로 받고 갱신합니다. (위 "1. 인프라 만들기")
- **ECS 구성**: ACM(서울 리전)에서 인증서 발급 → `certificate_arn` 설정 → `terraform apply` → 도메인 DNS를 `alb_dns_name`으로 연결 → Deploy 다시 실행.
  지금 ECS 구성은 API만 서비스합니다. 전환 전에 화면 제공(웹 이미지를 ECS 서비스로 띄우거나 S3 + CloudFront)을 추가해야 합니다.

## 알아 둘 점

- **DB 스키마**: 지금은 `ddl-auto=update`로 서버가 뜰 때 테이블을 만들고 고칩니다. 컬럼 삭제·이름 변경은 반영되지 않고, 잘못된 엔티티 변경이 운영 DB를 바꿀 수 있습니다. 실제 데이터가 쌓이기 전에 Flyway 같은 마이그레이션 도구를 도입하고 `ddl_auto = "validate"`로 바꾸는 것을 권장합니다.
- **ECS에서 서버가 2대**: `@Scheduled` 작업(포인트 환전 처리)이 두 서버에서 모두 돕니다. 같은 건을 두 서버가 동시에 처리하지 않는지 전환 전에 확인이 필요합니다. (EC2 구성은 한 대라 해당 없음)
- **배포 중 WebSocket**: 서버가 교체될 때 기존 연결이 끊깁니다. 클라이언트는 자동으로 다시 연결해야 합니다.
- **WebSocket 허용 출처**: 지금은 모든 출처를 허용합니다(`WebSocketConfig`). 프론트 도메인이 정해지면 좁히세요.
- **EC2 서버 교체**: 서버 디스크에는 데이터가 없어 EC2를 다시 만들어도 잃는 것이 없습니다. 다만 Terraform은 apply할 때마다 서버가 바뀌어 접속이 끊기지 않도록, AMI·부팅 스크립트가 바뀌어도 서버를 교체하지 않습니다. 새로 만들려면 위 "서버를 새로 만들기"를 따르세요.
- **RDS 삭제 방지**: RDS는 `db_deletion_protection = true`(기본값)라 실수로 `terraform destroy`해도 지워지지 않습니다.

## 정리 (전부 지우기)

```bash
cd infra
# RDS 삭제 방지를 먼저 풀어야 한다: terraform.tfvars에 db_deletion_protection = false 추가 후 apply
terraform destroy
```

ECR에 이미지가 남아 있으면 저장소 삭제가 실패할 수 있습니다. ECR 콘솔에서 이미지를 지운 뒤 다시 실행하세요.
남은 EBS·RDS 스냅샷도 저장 비용이 드니, 필요 없으면 콘솔에서 지우세요.
