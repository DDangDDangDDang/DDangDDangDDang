# 땅땅땅 프론트엔드

서비스용 웹 화면입니다. AI가 작성하며, 작업 규칙은 [CLAUDE.md](CLAUDE.md)에 있습니다.
(개발 확인용 화면은 `test-client/`에 따로 있습니다.)

## 실행

```bash
cd frontend
npm install
npm run dev
```

- http://localhost:5173 을 엽니다. 백엔드(`./gradlew bootRun`, 8080)와 MySQL·Redis(`docker compose up -d`)가 떠 있어야 API가 동작합니다.
- 공통 컴포넌트 모음: http://localhost:5173/dev/components

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
