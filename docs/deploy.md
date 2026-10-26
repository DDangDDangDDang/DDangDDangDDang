# AWS 배포 가이드

main 브랜치에 머지되면 GitHub Actions가 테스트 → 이미지 빌드 → 배포까지 자동으로 합니다.
배포 구성은 두 가지이고, 같은 Terraform 코드(`infra/`)에서 스위치로 고릅니다.

| | EC2 (개발 중, 지금) | ECS (운영) |
|---|---|---|
| 스위치 | `ec2_enabled = true`, `ecs_enabled = false` | `ec2_enabled = false`, `ecs_enabled = true` |
| 앱 | EC2 1대에서 Docker Compose로 실행 | ALB + ECS Fargate 2대 + NAT |
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
브라우저 ──HTTP:80──▶ EC2 (Elastic IP, 퍼블릭 서브넷)
                       └ Docker Compose: app(8080)
                            ├─▶ RDS MySQL 8.4        (프라이빗 서브넷)
                            ├─▶ ElastiCache Redis 7  (프라이빗 서브넷)
                            └─▶ 외부 API (인터넷 게이트웨이)
GitHub Actions ──OIDC──▶ ECR에 이미지 푸시 ──SSM Run Command──▶ EC2에서 deploy.sh 실행

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
| `src/main/resources/application-prod.yml` | 운영 설정 (비밀값 기본값 없음, 프록시 헤더, SQL 로그 끔) |
| `infra/*.tf` | AWS 리소스 전체. `ec2.tf`는 EC2 구성, `ecs.tf`·`alb.tf`는 ECS 구성, `data_stores.tf`(RDS·ElastiCache)는 공통 |
| `infra/ec2/` | EC2 서버의 `docker-compose.yml`(앱만), `deploy.sh`, 첫 부팅 스크립트 |
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
| Secrets Manager, ECR | 1~2달러 |

합계 약 **$65 / 월**. NAT·ALB가 없어 ECS 구성보다 고정비가 작습니다.
(AWS 프리 티어 대상 계정이면 처음 12개월은 EC2·RDS·ElastiCache 일부가 무료일 수 있습니다. 인스턴스 크기가 프리 티어 대상인지 확인하세요.)

**ECS 구성 (운영)**: NAT 약 $43, Fargate 2대 약 $41, RDS 약 $22, ALB 약 $20, ElastiCache 약 $18, 공인 IPv4 약 $11 등 합계 약 **$160 / 월**.

모두 트래픽이 적을 때의 어림값입니다. 정확한 값은 [AWS 요금 계산기](https://calculator.aws/)로 확인하세요.

## 0. 준비물

- AWS 계정, 그리고 관리자 권한이 있는 IAM 사용자(또는 IAM Identity Center) 로그인
  - 루트 계정으로 작업하지 마세요.
- [AWS CLI](https://aws.amazon.com/cli/) v2: `aws configure` 또는 `aws configure sso`로 로그인
- [Terraform](https://developer.hashicorp.com/terraform/install) 1.10 이상
- [LiveKit Cloud](https://cloud.livekit.io) 프로젝트 1개 (무료 플랜 가능): Project URL, API Key, API Secret
- 서버 접속용 [Session Manager 플러그인](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) (선택)

```bash
aws sts get-caller-identity   # 로그인된 계정 확인
terraform -version
```

## 1. 인프라 만들기 (EC2 구성)

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # livekit_url 채우기. ec2_enabled = true, ecs_enabled = false
terraform init
terraform plan     # 만들 리소스 확인
terraform apply    # 약 10~15분 (RDS·ElastiCache 생성이 가장 오래 걸린다)
```

EC2는 켜진 뒤 1~2분 동안 Docker를 설치합니다. 앱은 4단계의 첫 배포 때 뜹니다.
DB 테이블은 첫 배포 때 앱이 만듭니다(`ddl-auto=update`).

> **상태 파일 주의**: `infra/terraform.tfstate`에 DB 비밀번호가 들어 있습니다. 커밋하지 마세요. (`.gitignore`에 있음)
> 이 파일을 잃어버리면 Terraform으로 리소스를 고치거나 지울 수 없습니다. 팀원과 같이 관리하려면 S3에 두세요. (`infra/versions.tf`의 `backend "s3"` 주석 참고)

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
  "WEB_PUSH_PRIVATE_KEY": "<Private Key>"
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
| `ECS_CLUSTER`, `ECS_SERVICE`, `ECS_TASK_FAMILY`, `ECS_CONTAINER_NAME` | `ddang`, `ddang-app`, `ddang-app`, `app` | ECS (전환할 때 추가) |

4. **배포 대상**은 Environment가 아니라 **저장소 변수**로 넣습니다.
   **Settings → Secrets and variables → Actions → Variables → New repository variable**
   - 이름 `DEPLOY_TARGET`, 값 `ec2`
   - (워크플로가 어느 배포 작업을 실행할지 Environment에 들어가기 전에 정하기 때문입니다.)
   - 이 변수가 없으면 main에 머지돼도 테스트만 하고 빌드·배포는 건너뜁니다. AWS를 만들기 전에는 비워 두세요.

AWS 액세스 키는 GitHub에 넣지 않습니다. 배포는 OIDC로 역할을 잠깐 빌려서 합니다.

## 4. 첫 배포

`main`에 머지하면 자동으로 배포됩니다. 처음에는 **Actions → Deploy → Run workflow**(branch: `main`)로 직접 실행해도 됩니다.

진행 순서: 배포 대상 확인 → 테스트 → 이미지 빌드·ECR 푸시 → EC2 배포
- EC2 배포는 SSM으로 서버의 `deploy.sh`를 실행합니다. 새 이미지로 바꾼 뒤 헬스 체크가 3분 안에 통과하지 않으면 이전 이미지로 되돌리고 워크플로를 실패로 표시합니다.
- 서버 출력(앱 로그 일부 포함)은 워크플로의 "서버에서 배포 스크립트 실행" 단계에서 볼 수 있습니다.

## 5. 확인

```bash
cd infra
curl "$(terraform output -raw app_url)/actuator/health/liveness"   # {"status":"UP"}
```

서버 접속 (SSH 대신 Session Manager):

```bash
aws ssm start-session --target "$(terraform output -raw ec2_instance_id)"
sudo -i
cd /opt/ddang
docker compose ps
docker compose logs -f app
```

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

**4) EC2 끄기**

```hcl
ec2_enabled = false
ecs_enabled = true
```

```bash
terraform apply   # EC2, Elastic IP 삭제. RDS·ElastiCache는 그대로
```

GitHub의 `EC2_INSTANCE_ID` 변수는 지워도 됩니다. 도메인을 쓰고 있었다면 DNS를 ALB 주소로 바꿉니다.

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

## 도메인·HTTPS 연결 (나중에)

- **ECS 구성**: ACM(서울 리전)에서 인증서 발급 → `certificate_arn` 설정 → `terraform apply` → 도메인 DNS를 `alb_dns_name`으로 연결 → Deploy 다시 실행
- **EC2 구성**: 지금은 HTTP만 지원합니다. HTTPS가 필요하면 EC2 앞에 Caddy·Nginx 같은 리버스 프록시를 두거나, ECS로 전환할 때 붙이는 것을 권장합니다.

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
