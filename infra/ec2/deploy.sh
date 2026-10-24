#!/bin/bash
# EC2 구성의 배포 스크립트. GitHub Actions가 SSM Run Command로 서버에서 실행한다.
#   사용법: deploy.sh <이미지 URI>
# 1) 최신 docker-compose.yml과 앱 환경변수를 SSM 파라미터에서 받는다.
# 2) 비밀값을 Secrets Manager에서 읽어 .env / app.env를 만든다. (서버 디스크에만 남고 권한은 root 전용)
# 3) 새 이미지·설정으로 앱 컨테이너를 바꾸고, 헬스 체크가 통과하지 않으면 직전 배포의 이미지·설정으로 되돌린다.
set -euo pipefail

IMAGE="${1:?이미지 URI가 필요합니다}"
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
app_secret_json="$(get_secret "$APP_SECRET_ID")"
db_secret_json="$(get_secret "$DB_SECRET_ID")"
db_json="$(jq '{DB_USERNAME: .username, DB_PASSWORD: .password}' <<<"$db_secret_json")"

{ to_env <<<"$app_env_json"; to_env <<<"$app_secret_json"; to_env <<<"$db_json"; } > app.env.new
echo "APP_IMAGE='$IMAGE'" > .env.new

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY" >/dev/null

# 실패하면 이미지뿐 아니라 설정(compose, 환경변수)까지 직전 배포 상태로 되돌릴 수 있게 백업한다.
CONFIG_FILES=(docker-compose.yml app.env .env)
previous_image="$(cat .current_image 2>/dev/null || true)"
for f in "${CONFIG_FILES[@]}"; do
  rm -f "$f.prev"
  if [ -f "$f" ]; then cp -p "$f" "$f.prev"; fi
done
for f in "${CONFIG_FILES[@]}"; do
  mv "$f.new" "$f"
done

echo "배포 시작: $IMAGE"
docker compose pull app
docker compose up -d --remove-orphans

healthy=false
for _ in $(seq 1 36); do # 최대 3분
  if curl -fsS http://localhost/actuator/health/liveness >/dev/null 2>&1; then
    healthy=true
    break
  fi
  sleep 5
done

if [ "$healthy" = true ]; then
  echo "$IMAGE" > .current_image
  # 디스크가 차지 않도록 앱 이미지 중 지금 버전과 직전 버전(빠른 되돌리기용)만 남긴다.
  docker images --format '{{.Repository}}:{{.Tag}}' \
    | grep "^${REGISTRY}/" \
    | grep -vxF -e "$IMAGE" -e "${previous_image:-none}" \
    | xargs -r docker rmi >/dev/null 2>&1 || true
  echo "배포 완료: $IMAGE"
  exit 0
fi

echo "헬스 체크 실패. 앱 로그:"
docker compose logs --tail 100 app || true

if [ -f .env.prev ]; then
  echo "직전 배포의 이미지와 설정으로 되돌립니다: ${previous_image:-알 수 없음}"
  for f in "${CONFIG_FILES[@]}"; do
    if [ -f "$f.prev" ]; then mv "$f.prev" "$f"; fi
  done
  docker compose up -d --remove-orphans
else
  echo "첫 배포라 되돌릴 이전 상태가 없습니다."
fi
exit 1
