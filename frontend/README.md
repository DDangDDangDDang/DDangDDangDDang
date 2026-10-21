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
- 포인트 충전(포트원 결제창)을 쓰려면 `frontend/.env.local`에 포트원 콘솔의 상점 ID와 채널 키를 적습니다. 없으면 충전 화면에서 결제 버튼이 막힙니다. 결제 확인은 백엔드의 `PORTONE_API_SECRET`이 필요합니다.
  ```
  VITE_PORTONE_STORE_ID=store-...
  VITE_PORTONE_CHANNEL_KEY=channel-key-...   # 테스트 채널 키를 쓰면 실제 결제가 되지 않음
  ```

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

## E2E 테스트 (Playwright)

네 흐름을 **로컬 백엔드**에 대고 실행합니다. 백엔드(MySQL·Redis·LiveKit 포함)를 먼저 띄웁니다.

- 방송 시청: 로그인 → 홈 → 방송 시청 → 채팅 전송
- 판매자 송출: 스튜디오에서 방송 만들기 → 가짜 카메라로 방송 시작 → 시청 화면에 영상이 나옴 → 새로고침 확인 창 → 새로고침 뒤 이어서 송출 → 채팅 → 다른 화면으로 가도 송출이 이어짐(작은 방송 창) → 스튜디오에서 상품 등록·경매 시작 → 방송 종료
- 경매 등록: 상품 등록 + 경매 일정 → 경매 상세 → 경매 목록 검색 → 경매 취소 (소분류 카테고리가 있어야 함. 없으면 건너뜀)
- 1:1 채팅: 시청자가 판매자에게 문의 → 판매자가 실시간으로 받고 읽음("1" 사라짐) → 답장 → 신고 → 차단·해제

1. 테스트 계정 두 개(판매자·시청자)를 회원가입 화면에서 만듭니다. 로컬은 휴대폰 인증번호가 **백엔드 로그**에 찍힙니다. (`LogSmsSender ... 인증번호 [123456]`)
2. `frontend/.env.e2e.local`에 계정을 적습니다. (`*.local`이라 커밋되지 않음)
   ```
   E2E_EMAIL=시청자 이메일
   E2E_PASSWORD=시청자 비밀번호
   E2E_SELLER_EMAIL=판매자 이메일      # 없으면 시청자 계정으로 방송을 엶 (판매자 송출 테스트는 건너뜀)
   E2E_SELLER_PASSWORD=판매자 비밀번호
   E2E_BROWSER_CHANNEL=chrome          # PC의 Chrome 사용. 비우면 npx playwright install chromium 필요
   ```
3. 실행합니다. 프론트(5173)가 떠 있지 않으면 자동으로 띄웁니다. 다른 주소면 `E2E_BASE_URL`.
   ```bash
   npm run e2e
   ```

테스트는 판매자 계정으로 방송을 만들어 시작하고, 끝나면 테스트가 시작한 방송만 종료합니다. (판매자 계정에 종료되지 않은 방송이 있으면 그 방송을 씀)
카메라·마이크는 Chrome의 가짜 장치(`--use-fake-device-for-media-stream`, 테스트 패턴 영상)를 쓰므로 PC에 카메라가 없어도 됩니다.
카테고리는 관리자 계정으로 `/admin/categories`에서 만듭니다. (로컬은 DB에서 `users.role`을 `ADMIN`으로 바꾼 계정으로 로그인)
결과 보고서는 `playwright-report/`에 남고, 판매자 송출 테스트는 송출 중인 스튜디오·시청 화면 스크린샷을 보고서에 붙입니다.
