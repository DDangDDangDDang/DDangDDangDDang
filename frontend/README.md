# 땅땅땅 프론트엔드

서비스용 웹 화면입니다. AI가 작성하며, 작업 규칙은 [CLAUDE.md](CLAUDE.md)에 있습니다.
(개발 확인용 화면은 `test-client/`에 따로 있습니다.)

## 실행

API까지 쓰려면 백엔드를 먼저 띄웁니다. **아래 두 명령은 저장소 루트**(`frontend`가 아닌 상위 폴더)에서 실행합니다.

```bash
# 저장소 루트에서
docker compose up -d   # MySQL·Redis·LiveKit (MySQL을 PC에 직접 설치했다면: docker compose up -d redis livekit)
./gradlew bootRun      # 백엔드 (8080)
```

그다음 **다른 터미널에서 `frontend` 폴더**로 이동해 프론트를 띄웁니다.

```bash
# frontend 폴더에서
npm install
npm run dev
```

- http://localhost:5173 을 엽니다. 화면만 볼 때는 백엔드 없이도 됩니다. (로그인 복구 요청이 실패한 오류가 콘솔에 남는 건 정상)
- 공통 컴포넌트 모음: http://localhost:5173/dev/components
- 백엔드를 다른 포트로 띄웠다면 `frontend/.env.local`에 `BACKEND_URL=http://localhost:8081`을 적습니다.

## 자주 쓰는 명령

| 명령 | 하는 일 |
|---|---|
| `npm run dev` | 개발 서버 (백엔드로 `/api`, `/ws` 프록시) |
| `npm run gen:api` | 백엔드 명세(`docs/api/openapi.json`)로 API 타입·에러 코드 다시 생성 (dev·build·typecheck·test 때 자동 실행) |
| `npm run check` | 타입 검사 + 린트 + 단위 테스트 |
| `npm run build` | 배포용 빌드 (`dist/`) |

## 백엔드 개발자가 알아 둘 것

- **추가로 할 일은 없습니다.** 지금처럼 백엔드 테스트로 갱신된 `docs/api/openapi.json`만 커밋하면, 프론트는 빌드·검사 때마다 그 명세로 API 타입을 다시 만듭니다.
- API를 바꿔서(필드 이름 변경·삭제 등) 화면 코드가 맞지 않게 되면 CI의 **"프론트엔드 검사"가 타입 오류로 실패**합니다. 실패 위치가 고쳐야 할 화면이고, 프론트 담당 AI 세션에 맡기면 됩니다.
