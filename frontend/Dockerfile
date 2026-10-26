# EC2 구성의 웹 이미지: 프론트엔드 빌드 결과 + Caddy(HTTPS 인증서 자동 발급, /api·/ws를 앱으로 전달)
# API 타입을 명세(docs/api/openapi.json)에서 만들기 때문에 저장소 루트를 빌드 컨텍스트로 쓴다.
#   docker build -f frontend/Dockerfile -t ddang-web .
# 빌드 컨텍스트에 들어가는 파일은 frontend/Dockerfile.dockerignore가 정한다. (.env 파일은 들어가지 않는다)

# 1단계: 화면 빌드 (CI와 같은 Node 24)
FROM node:24-slim AS build
WORKDIR /workspace/frontend

# 의존성 설치를 소스 변경과 분리해 Docker 레이어 캐시를 살린다.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY docs/api/openapi.json /workspace/docs/api/openapi.json
COPY frontend ./

# 브라우저에 그대로 노출되는 공개 값이라 빌드할 때 넣는다. 비어 있으면 해당 버튼을 누를 때 "설정이 필요합니다"라고 안내한다.
# 소셜 로그인 redirect URI는 비워 두면 접속한 주소의 /oauth/{provider}/callback을 쓴다.
ARG VITE_KAKAO_REST_API_KEY=""
ARG VITE_GOOGLE_CLIENT_ID=""
ARG VITE_PORTONE_STORE_ID=""
ARG VITE_PORTONE_CHANNEL_KEY=""
RUN npm run build

# 2단계: 정적 파일 + 리버스 프록시
FROM caddy:2.10-alpine
COPY infra/ec2/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /workspace/frontend/dist /srv
