# API 명세

프론트엔드(AI가 작성)는 이 폴더의 명세만 보고 API를 호출합니다. 백엔드 코드를 추측해서 쓰지 않도록, 명세는 코드에서 자동으로 만들고 CI가 최신인지 확인합니다.

| 파일 | 내용 |
|---|---|
| [`openapi.json`](openapi.json) | REST API 명세 (OpenAPI 3). springdoc이 컨트롤러·DTO에서 자동 생성 |
| 이 문서의 [실시간 메시지](#실시간-메시지-stomp) | WebSocket(STOMP) 경로와 메시지 형식. OpenAPI에는 들어가지 않아 직접 정리 |

## REST API 명세 (`openapi.json`)

### 갱신하기

따로 할 일은 없습니다. **로컬에서 테스트를 돌리면** `OpenApiSpecTest`가 명세가 바뀌었을 때 `openapi.json`을 자동으로 다시 씁니다.
API(컨트롤러, 요청·응답 DTO, 에러 코드)를 바꿨다면 `git status`에 `docs/api/openapi.json`이 바뀐 것으로 나오니, **같은 커밋에 포함**하면 됩니다.

```bash
./gradlew test
```

IntelliJ에서 테스트를 실행해도 같습니다. 명세 테스트만 빠르게 돌리려면 `./gradlew test --tests '*OpenApiSpecTest'`를 쓰면 됩니다. (Docker 불필요)

CI(`빌드 · 테스트`)에서는 파일을 쓰지 않고 비교만 합니다. 바뀐 명세를 커밋하지 않고 push하면 CI가 실패하니, 로컬에서 테스트를 돌리고 바뀐 파일을 커밋하세요.

API를 만들 때 명세가 실제 동작과 맞도록 두 가지를 지켜 주세요.
- **200이 아닌 성공 코드로 응답하는 API**에는 메서드에 `@ResponseStatus`를 붙입니다. 명세는 `ResponseEntity.status(...)`의 값을 알 수 없어서, 없으면 200으로 나옵니다.
  - 생성(`HttpStatus.CREATED`) → `@ResponseStatus(HttpStatus.CREATED)`
  - 본문 없음(`HttpStatus.NO_CONTENT`, `noContent()`) → `@ResponseStatus(HttpStatus.NO_CONTENT)`
- **토큰 없이 호출하는 API**는 POST면 `SecurityConfig.PUBLIC_POST_APIS`에, GET이면 `SecurityConfig.PUBLIC_GET_APIS`에 추가합니다. 보안 설정과 명세의 "인증 불필요" 표시가 이 목록으로 같이 바뀝니다.

### 보기

로컬에서 서버를 띄운 뒤 브라우저로 엽니다. 운영(prod)에서는 꺼져 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- 명세(JSON): http://localhost:8080/v3/api-docs

오른쪽 위 **Authorize**에 로그인 응답의 `accessToken`을 넣으면 인증이 필요한 API도 호출해 볼 수 있습니다.

### 명세에 담긴 규칙

- **응답 형식**: 본문이 있는 응답은 모두 `ApiResponse` 형태입니다.
  - 성공: `{ "success": true, "data": { ... }, "error": null }`
  - 실패: `{ "success": false, "data": null, "error": { "code": "AUCTION_001", "message": "..." } }`
  - 실패 응답은 모든 API에 `4XX`·`5XX` 응답(`ApiErrorResponse`)으로 들어 있습니다.
  - **예외: `204 No Content`** (경매 취소, 상품 삭제, 카테고리 삭제 등)는 본문이 없습니다. JSON으로 파싱하지 마세요. 명세에 `204`로 표시됩니다.
- **에러 코드 목록**: 명세 최상위의 `x-error-codes`에 모든 에러 코드의 코드, HTTP 상태, 기본 메시지가 있습니다. 화면에 보여줄 문구는 `error.code`를 기준으로 정합니다. (`message`는 검증 오류 등에서 내용이 바뀔 수 있음)
- **인증**: `Authorization: Bearer {accessToken}`. 로그인 전 API(회원가입, 로그인, 토큰 재발급 등)는 토큰 없이 호출합니다.
- **토큰 재발급**: 리프레시 토큰은 HttpOnly 쿠키(`refreshToken`)로 주고받습니다. 프론트는 쿠키를 직접 다루지 않고, 같은 주소(로컬은 Vite 프록시)로 요청해 브라우저가 쿠키를 보내게 합니다.
- **함수 이름**: `operationId`는 "컨트롤러 이름 + 메서드 이름"입니다. (예: `AuctionController.cancelAuction` → `auctionCancelAuction`) 코드 생성기가 이 이름으로 함수를 만듭니다.

## 실시간 메시지 (STOMP)

### 연결

- 주소: `ws://{호스트}/ws` (STOMP over WebSocket, SockJS 없음)
- 인증: STOMP `CONNECT` 프레임 헤더에 `Authorization: Bearer {accessToken}`
  - 브라우저 WebSocket은 핸드셰이크에 헤더를 붙일 수 없어서, 핸드셰이크는 열려 있고 인증은 CONNECT에서 합니다.
  - **토큰 없이 CONNECT하면 손님 연결**입니다. 손님은 방송 토픽(`/topic/live-streams/{id}`, `/topic/live-streams/{id}/chat`, `/topic/live-streams/{id}/auctions`)와 경매 토픽(`/topic/auctions/{id}`)만 구독할 수 있고, 보내기(`/app/...`)와 `/user/queue/...`는 할 수 없습니다. (로그인하지 않은 사람도 방송 채팅을 볼 수 있게)
  - 토큰이 있는데 잘못됐으면(만료·로그아웃) 손님으로 바꾸지 않고 ERROR 프레임으로 거부합니다. 클라이언트는 토큰을 재발급해 다시 연결합니다.
- 보내기는 `/app/...`, 받기는 `/topic/...`(구독자 전체) 또는 `/user/queue/...`(나에게만)입니다.
- 아래 표에 없는 경로는 구독할 수 없습니다. (서버가 거부)

### 보내기

| 경로 | 본문 | 설명 |
|---|---|---|
| `/app/live-streams/{liveStreamId}/chat` | `{ "content": string }` (1~200자) | 방송 채팅 보내기. 방송 중일 때만 가능. 실패하면 `/user/queue/errors`로 이유가 옴 |

### 구독

| 경로 | 받는 사람 | 메시지 |
|---|---|---|
| `/topic/live-streams/{liveStreamId}/chat` | 방송 시청자 전체 (손님 포함) | `ChatMessageResponse` |
| `/topic/live-streams/{liveStreamId}` | 방송 시청자 전체 (손님 포함) | `LiveStreamUpdatedMessage` (방송 정보 변경) |
| `/topic/live-streams/{liveStreamId}/auctions` | 방송 시청자 전체 (손님 포함) | `LiveAuctionChangedMessage` (방송에 올라간 경매가 시작·추가·취소·마감됨) |
| `/topic/auctions/{auctionId}` | 경매를 보는 사람 전체 (손님 포함) | `AuctionRealtimeMessage` (입찰이 반영됨 / 경매가 끝남) |
| `/user/queue/errors` | 나 | `ErrorResponse` (채팅 전송 실패 등) |
| `/user/queue/moderation` | 방송 판매자 | `ModerationAlert` (AI가 확인이 필요하다고 본 채팅) |
| `/user/queue/live-stream` | 방송 판매자 | `LiveStreamIdleMessage` (송출이 없어 곧 자동 종료됨·취소됨·종료됨) |
| `/user/queue/dm` | 나 | `DmEvent` (1:1 채팅 새 메시지, 읽음) |
| `/user/queue/notifications` | 나 | `NotificationResponse` (구독한 판매자의 방송 시작, 경매 시작·상위 입찰·마감 10분 전·취소·낙찰·유찰) |

### 메시지 형식

`LocalDateTime`은 `"2026-10-20T16:30:00"`처럼 시간대 없는 서울 시각 문자열입니다.

**ChatMessageResponse**

| 필드 | 타입 | 설명 |
|---|---|---|
| `messageId` | string | 메시지 ID (UUID). 검토 알림이 가리키는 메시지를 찾을 때 씀 |
| `type` | `"CHAT"` \| `"SYSTEM"` \| `"DONATION"` \| `"BID"` \| `"AUCTION_SOLD"` \| `"MESSAGE_HIDDEN"` \| `"MESSAGE_RESTORED"` | `SYSTEM`은 서버 안내(방송 시작·종료 등). 이때 `senderId`, `nickname`은 null. `DONATION`은 도네이션 포인트 선물, `BID`는 방송에 올라간 경매의 입찰, `AUCTION_SOLD`는 낙찰. `MESSAGE_HIDDEN`·`MESSAGE_RESTORED`는 아래 "채팅 가림" 참고 |
| `liveStreamId` | number | |
| `senderId` | number \| null | `DONATION`이면 선물한 사람, `BID`면 입찰한 사람, `AUCTION_SOLD`면 낙찰받은 사람 |
| `nickname` | string \| null | `senderId`의 닉네임 |
| `content` | string \| null | `DONATION`이면 선물과 함께 보낸 한마디 (없으면 null). `BID`·`AUCTION_SOLD`면 채팅에 그대로 보여 줄 안내 문장 |
| `sentAt` | string | |
| `amount` | number \| null | `DONATION`이면 선물한 포인트, `BID`면 입찰 금액, `AUCTION_SOLD`면 낙찰가 |
| `previousAmount` | number \| null | `BID`에만 있음. 이 입찰 직전의 현재가 (첫 입찰이면 시작가). 오른 비율로 이펙트 크기를 정함 |
| `auctionId` | number \| null | `BID`·`AUCTION_SOLD`에만 있음 |
| `productName` | string \| null | `BID`·`AUCTION_SOLD`에만 있음 |
| `badge` | `"ANGEL_WINGS"` \| `"DEVIL_WINGS"` \| `"HALO"` \| `"STAR"` \| null | `CHAT`에만 있음. 보낸 사람이 장착한 닉네임 뱃지 (없으면 null). 닉네임을 꾸며서 보여 줌 |
| `targetMessageId` | string \| null | `MESSAGE_HIDDEN`·`MESSAGE_RESTORED`에만 있음. 가리거나 되돌릴 메시지의 `messageId` |

- `DONATION`은 `POST /api/v1/live-streams/{id}/donations`가 커밋된 뒤에 옵니다. 방송 화면 한쪽에 "`nickname`님이 `amount` P를 선물했어요" 이펙트를 띄우고, 채팅에도 한 줄 남깁니다.
- **채팅 가림**: 방송 설정 `chatModerationMode`가 `AUTO_HIDE`이고 AI 검토가 명백한 위반이라고 보면, 채팅(또는 선물 한마디)을 보낸 몇 초 뒤 `MESSAGE_HIDDEN`이 옵니다. 새 줄을 붙이지 않고 `targetMessageId` 메시지의 내용을 "가려진 메시지"로 바꿉니다. 판매자가 `POST /api/v1/live-streams/{id}/chat/messages/{messageId}/restore`로 되돌리면 `MESSAGE_RESTORED`가 오고 원래 내용을 다시 보여 줍니다. 채팅은 서버에 저장하지 않으므로 나중에 들어온 시청자에게는 오지 않습니다.
- `BID`는 방송에 올라간 경매에 입찰이 커밋된 뒤, `AUCTION_SOLD`는 그 경매가 마감 때 낙찰되거나 즉시 구매로 팔린 뒤에 옵니다. 방송 화면에 이펙트를 띄우고 채팅에도 한 줄 남깁니다. (유찰·취소는 오지 않음)

**LiveStreamUpdatedMessage**: `{ "type": "UPDATED", "liveStream": LiveStreamResponse }`
- `liveStream`은 REST의 `LiveStreamResponse`와 같은 형식입니다. 바뀐 값만이 아니라 전체 정보이므로 받은 값으로 화면을 덮어쓰면 됩니다.
- 방송 정보 수정뿐 아니라 방송을 시작할 때도 옵니다. (`status`가 `LIVE`) 방송 예정 화면에서 기다리던 시청자는 새로고침 없이 영상에 접속하고, 채팅에 `SYSTEM` "방송이 시작되었습니다." 안내도 옵니다.

**브라우저 알림(웹 푸시)**: 탭을 닫아도 브라우저가 켜져 있으면 같은 알림이 브라우저 알림으로 옵니다.
- `GET /api/v1/push/public-key`로 받은 키로 `pushManager.subscribe` → `POST /api/v1/push/subscriptions`로 저장, 로그아웃·끄기는 `DELETE /api/v1/push/subscriptions?endpoint=`
- 서비스 워커(`frontend/public/sw.js`)가 받는 내용: `{ notificationId, type, title, body, link, tag }`
- 서버 설정: `WEB_PUSH_PUBLIC_KEY`·`WEB_PUSH_PRIVATE_KEY`(VAPID, 비우면 서버를 켤 때마다 새로 만듦 — 로컬 전용), `WEB_PUSH_SUBJECT`

**AuctionRealtimeMessage**: 경매 한 건의 변경. 두 가지 모양이 옵니다.
- 입찰이 반영됨: `{ "auctionId", "bidderId", "amount", "bidCount", "endAt", "extended" }` — `amount`가 새 현재가, 마감 30초 전 안쪽 입찰이면 `extended: true`이고 `endAt`이 늘어납니다.
- 경매가 끝남: `{ "auctionId", "result": "ENDED" | "FAILED", "sellerId", "winnerId", "finalPrice", "productName" }` — 낙찰(즉시 구매 포함)·유찰. 받으면 경매를 다시 불러옵니다.

**LiveAuctionChangedMessage**: `{ "type": "CHANGED", "auctionId": number }`
- 바뀐 경매 번호만 옵니다. 받으면 방송의 경매 목록(`GET /api/v1/lives/{liveId}/auctions`)을 다시 불러옵니다.
- 경매가 시작되거나 방송에 새로 올라오면 채팅(`/topic/live-streams/{id}/chat`)에도 `SYSTEM` 안내가 옵니다.

**ErrorResponse**: `{ "code": string, "message": string }`. REST의 `error`와 같고, 코드는 `x-error-codes`에 있습니다.

**ModerationAlert**

| 필드 | 타입 | 설명 |
|---|---|---|
| `liveStreamId` | number | |
| `messageId` | string | 문제의 채팅 `messageId` |
| `messageType` | `"CHAT"` \| `"DONATION"` | 채팅인지 선물 한마디인지 |
| `senderId` | number | |
| `nickname` | string | |
| `content` | string | |
| `category` | `"EXTERNAL_TRADE"` \| `"SCAM"` \| `"HARASSMENT"` \| `"HATE"` \| `"SEXUAL"` \| `"SPAM"` \| `"PERSONAL_INFO"` | |
| `categoryLabel` | string | 화면에 보여줄 분류 이름 |
| `reason` | string | 판단 이유 |
| `autoHidden` | boolean | 방송 설정(`AUTO_HIDE`)에 따라 이미 시청자 화면에서 가렸는지. 가렸으면 판매자가 되돌릴 수 있음 |
| `detectedAt` | string | |

- AI 검토는 서버에서 켰을 때만(`CHAT_MODERATION_AI_ENABLED=true` + `ANTHROPIC_API_KEY`) 동작하고, 방송 설정 `chatModerationMode`가 `OFF`면 그 방송은 검토하지 않습니다. `ALERT`(기본)는 알림만, `AUTO_HIDE`는 명백한 위반을 가리고 알립니다.
- 1:1 메시지도 같은 AI가 검토하지만 가리지 않고, 관리자 확인 목록(`GET /api/v1/admin/dm/moderation-flags`)에만 남깁니다.

**LiveStreamIdleMessage**: 방송 중(LIVE)인데 판매자 송출(영상·소리, 카메라·마이크를 모두 끈 상태 포함)이 30분 동안 없으면 서버가 방송을 종료합니다. 송출이 없던 시간은 마지막 송출부터, 한 번도 송출하지 않았으면 방송 시작부터 셉니다. (1분마다 확인)

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | `"IDLE_WARNING"` \| `"IDLE_RESUMED"` \| `"IDLE_ENDED"` | 송출이 25분 없어 곧 종료됨(종료될 때까지 1분마다 다시 옴) · 다시 송출해 자동 종료 취소 · 자동 종료됨(방송을 끝낸 뒤에 옴) |
| `liveStreamId` | number | |
| `endsAt` | string \| null | `IDLE_WARNING`일 때 자동 종료 예정 시각 |

- 자동 종료는 판매자가 [방송 종료]를 누른 것과 같습니다. (채팅 `SYSTEM` 종료 안내, 시청 화면 종료) 경매는 그대로 진행됩니다.

**DmEvent**

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | `"MESSAGE"` \| `"READ"` | |
| `roomId` | number | |
| `message` | DmMessageResponse \| null | `MESSAGE`일 때. REST의 `DmMessageResponse`와 같은 형식 |
| `readerId` | number \| null | `READ`일 때. 읽은 사람 |
| `lastReadMessageId` | number \| null | `READ`일 때. 여기까지 읽음 (보낸 사람 화면의 "1"을 지움) |

**NotificationResponse**: REST 알림함(`GET /api/v1/notifications`)의 항목과 같은 형식입니다. 알림함에 저장된 뒤에 오므로, 받은 뒤 알림함·안 읽은 수를 다시 불러와도 같은 알림이 보입니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | number | 알림 ID (읽음 처리 `POST /api/v1/notifications/{id}/read`) |
| `type` | string | 아래 표 |
| `title` | string | 화면에 그대로 보여 줄 제목. 브라우저 알림(웹 푸시)과 같은 문구 |
| `body` | string \| null | 설명 (방송 제목, 상품명·금액) |
| `link` | string | 누르면 갈 화면 경로 (`/lives/{id}`, `/auctions/{id}`) |
| `sellerId` | number \| null | `LIVE_STARTED`일 때 |
| `sellerNickname` | string \| null | `LIVE_STARTED`일 때. 알림을 만든 때의 닉네임 |
| `liveStreamId` | number \| null | `LIVE_STARTED`일 때. 시작한 방송 |
| `liveTitle` | string \| null | `LIVE_STARTED`일 때. 알림을 만든 때의 방송 제목 |
| `auctionId` | number \| null | `AUCTION_*`일 때 |
| `productName` | string \| null | `AUCTION_*`일 때. 알림을 만든 때의 상품명 |
| `price` | number \| null | `AUCTION_OUTBID`면 새 현재가, `AUCTION_WON`·`AUCTION_SOLD`면 낙찰가 |
| `read` | boolean | 실시간으로 올 때는 항상 false |
| `createdAt` | string | |

| `type` | 받는 사람 |
|---|---|
| `LIVE_STARTED` | 방송을 시작한 판매자의 구독자 |
| `AUCTION_STARTED` | 알림 받기(`PUT /api/v1/auctions/{id}/alert`)를 한 회원 |
| `AUCTION_OUTBID` | 최고 입찰자에서 밀려난 회원 |
| `AUCTION_CLOSING_SOON` | 알림 받기 + 입찰한 회원 (마감 10분 전, 경매마다 한 번) |
| `AUCTION_CANCELED` | 알림 받기 + 입찰한 회원 |
| `AUCTION_WON` | 낙찰자 (즉시 구매 포함) |
| `AUCTION_SOLD` / `AUCTION_FAILED` | 판매자 (낙찰됨 / 유찰) |
| `AUCTION_ENDED` | 그 밖의 알림 받기 + 입찰한 회원 |

### 실시간 메시지를 바꿀 때

실시간 메시지는 자동 생성되지 않습니다. 경로나 메시지 DTO를 바꾸면 **이 문서도 같은 PR에서 고쳐 주세요.**
관련 코드: `StompAuthChannelInterceptor`(구독 허용 경로), `LiveChatController`, `LiveAuctionEventListener`, `*Message`·`*Event`·`ModerationAlert` DTO.
