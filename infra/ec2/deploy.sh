#!/bin/bash
# EC2 구성의 배포 스크립트. GitHub Actions가 SSM Run Command로 서버에서 실행한다.
#   사용법: deploy.sh <앱 이미지 URI> <웹 이미지 URI>
# 1) 최신 docker-compose.yml과 앱·웹 환경변수를 SSM 파라미터에서 받는다.
# 2) 비밀값을 Secrets Manager에서 읽어 .env / app.env / web.env를 만든다. (서버 디스크에만 남고 권한은 root 전용)
# 3) 새 이미지·설정으로 컨테이너를 바꾸고, 헬스 체크가 통과하지 않으면 직전 배포의 이미지·설정으로 되돌린다.
set -euo pipefail

IMAGE="${1:?앱 이미지 URI가 필요합니다}"
WEB_IMAGE="${2:?웹 이미지 URI가 필요합니다}"
cd /opt/ddang
# shellcheck source=/dev/null
source ./config.sh # REGION, REGISTRY, PARAM_PREFIX, APP_SECRET_ID, DB_SECRET_ID (EC2 시작 시 user_data가 만든다)
umask 077

get_param() { aws ssm get-parameter --region "$REGION" --name "$PARAM_PREFIX/$1" --query Parameter.Value --output text; }
get_secret() { aws secretsmanager get-secret-value --region "$REGION" --secret-id "$1" --query SecretString --output text; }

# 값을 작은따옴표로 감싸 Compose가 $ 등을 해석하지 않게 한다. 작은따옴표·줄바꿈이 든 값은 쓸 수 없다.
to_env() {
  jq -r 'to_entries[]
    | if (.value | tostring | test("['\''\n]")) then error("값에 작은따옴표나 줄바꿈이 있습니다: \(.key)") else . end
    | "\(.key)='\''\(.value | tostring)'\''"'
}

# 새 설정은 .new 파일에 먼저 받는다. 받는 도중 실패해도 지금 쓰는 파일은 그대로 남는다.
get_param compose > docker-compose.yml.new
app_env_json="$(get_param app-env)"
web_env_json="$(get_param web-env)"
app_secret_json="$(get_secret "$APP_SECRET_ID")"
db_secret_json="$(get_secret "$DB_SECRET_ID")"
db_json="$(jq '{DB_USERNAME: .username, DB_PASSWORD: .password}' <<<"$db_secret_json")"

{ to_env <<<"$app_env_json"; to_env <<<"$app_secret_json"; to_env <<<"$db_json"; } > app.env.new
to_env <<<"$web_env_json" > web.env.new
printf "APP_IMAGE='%s'\nWEB_IMAGE='%s'\n" "$IMAGE" "$WEB_IMAGE" > .env.new

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null

# 실패하면 이미지뿐 아니라 설정(compose, 환경변수)까지 직전 배포 상태로 되돌릴 수 있게 백업한다.
CONFIG_FILES=(docker-compose.yml app.env web.env .env)
# 직전 배포의 이미지 목록 (한 줄에 하나: 앱, 웹)
previous_images="$(cat .current_images 2>/dev/null || true)"
for f in "${CONFIG_FILES[@]}"; do
  rm -f "$f.prev"
  if [ -f "$f" ]; then cp -p "$f" "$f.prev"; fi
done
for f in "${CONFIG_FILES[@]}"; do
  mv "$f.new" "$f"
done

echo "배포 시작: $IMAGE, $WEB_IMAGE"

# 앱은 서버 안에서 직접(127.0.0.1:8080) 확인하고, 웹(Caddy)은 컨테이너가 떠 있는지만 본다.
# 웹은 도메인으로만 응답하고 인증서는 DNS를 연결한 뒤에 발급되므로 여기서 HTTPS까지 확인하지 않는다.
# 이미지 받기·컨테이너 시작이 실패해도(포트 충돌 등) 스크립트를 바로 끝내지 않고 아래에서 이전 상태로 되돌린다.
healthy=false
if docker compose pull && docker compose up -d --remove-orphans; then
  for _ in $(seq 1 36); do # 최대 3분
    if curl -fsS http://127.0.0.1:8080/actuator/health/liveness >/dev/null 2>&1 \
      && docker compose ps --status running --services | grep -qx web; then
      healthy=true
      break
    fi
    sleep 5
  done
else
  echo "이미지를 받거나 컨테이너를 시작하지 못했습니다."
fi

if [ "$healthy" = true ]; then
  printf '%s\n%s\n' "$IMAGE" "$WEB_IMAGE" > .current_images
  # 디스크가 차지 않도록 저장소 이미지 중 지금 버전과 직전 버전(빠른 되돌리기용)만 남긴다.
  docker images --format '{{.Repository}}:{{.Tag}}' \
    | grep "^${REGISTRY}/" \
    | grep -vxF -e "$IMAGE" -e "$WEB_IMAGE" -f <(printf '%s\n' "${previous_images:-none}") \
    | xargs -r docker rmi >/dev/null 2>&1 || true
  echo "배포 완료: $IMAGE, $WEB_IMAGE"
  exit 0
fi

echo "헬스 체크 실패. 로그:"
docker compose ps || true
docker compose logs --tail 100 app web || true

if [ -f .env.prev ]; then
  echo "직전 배포의 이미지와 설정으로 되돌립니다:"
  echo "${previous_images:-알 수 없음}"
  for f in "${CONFIG_FILES[@]}"; do
    if [ -f "$f.prev" ]; then mv "$f.prev" "$f"; fi
  done
  docker compose up -d --remove-orphans
else
  echo "첫 배포라 되돌릴 이전 상태가 없습니다."
fi
exit 1
