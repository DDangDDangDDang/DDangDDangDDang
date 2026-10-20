# API 명세

프론트엔드(AI가 작성)는 이 폴더의 명세만 보고 API를 호출합니다. 백엔드 코드를 추측해서 쓰지 않도록, 명세는 코드에서 자동으로 만들고 CI가 최신인지 확인합니다.

| 파일 | 내용 |
|---|---|
| [`openapi.json`](openapi.json) | REST API 명세 (OpenAPI 3). springdoc이 컨트롤러·DTO에서 자동 생성 |
| 이 문서의 [실시간 메시지](#실시간-메시지-stomp) | WebSocket(STOMP) 경로와 메시지 형식. OpenAPI에는 들어가지 않아 직접 정리 |

## REST API 명세 (`openapi.json`)

### 갱신하기

API(컨트롤러, 요청·응답 DTO, 에러 코드)를 바꾸면 명세를 다시 만들어 **같은 커밋에 포함**합니다.

```bash
./gradlew test --tests '*OpenApiSpecTest' -PupdateOpenApi
```

갱신하지 않으면 `OpenApiSpecTest`가 실패해 PR의 CI(`빌드 · 테스트`)가 통과하지 않습니다.

### 보기

로컬에서 서버를 띄운 뒤 브라우저로 엽니다. 운영(prod)에서는 꺼져 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- 명세(JSON): http://localhost:8080/v3/api-docs

오른쪽 위 **Authorize**에 로그인 응답의 `accessToken`을 넣으면 인증이 필요한 API도 호출해 볼 수 있습니다.

### 명세에 담긴 규칙

- **응답 형식**: 모든 응답은 `ApiResponse` 형태입니다.
  - 성공: `{ "success": true, "data": { ... }, "error": null }`
  - 실패: `{ "success": false, "data": null, "error": { "code": "AUCTION_001", "message": "..." } }`
  - 실패 응답은 모든 API에 `4XX`·`5XX` 응답(`ApiErrorResponse`)으로 들어 있습니다.
- **에러 코드 목록**: 명세 최상위의 `x-error-codes`에 모든 에러 코드의 코드, HTTP 상태, 기본 메시지가 있습니다. 화면에 보여줄 문구는 `error.code`를 기준으로 정합니다. (`message`는 검증 오류 등에서 내용이 바뀔 수 있음)
- **인증**: `Authorization: Bearer {accessToken}`. 로그인 전 API(회원가입, 로그인, 토큰 재발급 등)는 토큰 없이 호출합니다.
- **토큰 재발급**: 리프레시 토큰은 HttpOnly 쿠키(`refreshToken`)로 주고받습니다. 프론트는 쿠키를 직접 다루지 않고, 같은 주소(로컬은 Vite 프록시)로 요청해 브라우저가 쿠키를 보내게 합니다.
- **함수 이름**: `operationId`는 "컨트롤러 이름 + 메서드 이름"입니다. (예: `AuctionController.cancelAuction` → `auctionCancelAuction`) 코드 생성기가 이 이름으로 함수를 만듭니다.

## 실시간 메시지 (STOMP)

### 연결

- 주소: `ws://{호스트}/ws` (STOMP over WebSocket, SockJS 없음)
- 인증: STOMP `CONNECT` 프레임 헤더에 `Authorization: Bearer {accessToken}`
  - 브라우저 WebSocket은 핸드셰이크에 헤더를 붙일 수 없어서, 핸드셰이크는 열려 있고 인증은 CONNECT에서 합니다.
- 보내기는 `/app/...`, 받기는 `/topic/...`(구독자 전체) 또는 `/user/queue/...`(나에게만)입니다.
- 아래 표에 없는 경로는 구독할 수 없습니다. (서버가 거부)

### 보내기

| 경로 | 본문 | 설명 |
|---|---|---|
| `/app/live-streams/{liveStreamId}/chat` | `{ "content": string }` (1~200자) | 방송 채팅 보내기. 방송 중일 때만 가능. 실패하면 `/user/queue/errors`로 이유가 옴 |

### 구독

| 경로 | 받는 사람 | 메시지 |
|---|---|---|
| `/topic/live-streams/{liveStreamId}/chat` | 방송 시청자 전체 | `ChatMessageResponse` |
| `/topic/live-streams/{liveStreamId}` | 방송 시청자 전체 | `LiveStreamUpdatedMessage` (방송 정보 변경) |
| `/user/queue/errors` | 나 | `ErrorResponse` (채팅 전송 실패 등) |
| `/user/queue/moderation` | 방송 판매자 | `ModerationAlert` (AI가 확인이 필요하다고 본 채팅) |
| `/user/queue/dm` | 나 | `DmEvent` (1:1 채팅 새 메시지, 읽음) |

### 메시지 형식

`LocalDateTime`은 `"2026-10-20T16:30:00"`처럼 시간대 없는 서울 시각 문자열입니다.

**ChatMessageResponse**

| 필드 | 타입 | 설명 |
|---|---|---|
| `messageId` | string | 메시지 ID (UUID). 검토 알림이 가리키는 메시지를 찾을 때 씀 |
| `type` | `"CHAT"` \| `"SYSTEM"` | `SYSTEM`은 서버 안내(방송 종료 등). 이때 `senderId`, `nickname`은 null |
| `liveStreamId` | number | |
| `senderId` | number \| null | |
| `nickname` | string \| null | |
| `content` | string | |
| `sentAt` | string | |

**LiveStreamUpdatedMessage**: `{ "type": "UPDATED", "liveStream": LiveStreamResponse }`
- `liveStream`은 REST의 `LiveStreamResponse`와 같은 형식입니다. 바뀐 값만이 아니라 전체 정보이므로 받은 값으로 화면을 덮어쓰면 됩니다.

**ErrorResponse**: `{ "code": string, "message": string }`. REST의 `error`와 같고, 코드는 `x-error-codes`에 있습니다.

**ModerationAlert**

| 필드 | 타입 | 설명 |
|---|---|---|
| `liveStreamId` | number | |
| `messageId` | string | 문제의 채팅 `messageId` |
| `senderId` | number | |
| `nickname` | string | |
| `content` | string | |
| `category` | `"EXTERNAL_TRADE"` \| `"SCAM"` \| `"HARASSMENT"` \| `"HATE"` \| `"SEXUAL"` \| `"SPAM"` \| `"PERSONAL_INFO"` | |
| `categoryLabel` | string | 화면에 보여줄 분류 이름 |
| `reason` | string | 판단 이유 |
| `detectedAt` | string | |

**DmEvent**

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | `"MESSAGE"` \| `"READ"` | |
| `roomId` | number | |
| `message` | DmMessageResponse \| null | `MESSAGE`일 때. REST의 `DmMessageResponse`와 같은 형식 |
| `readerId` | number \| null | `READ`일 때. 읽은 사람 |
| `lastReadMessageId` | number \| null | `READ`일 때. 여기까지 읽음 (보낸 사람 화면의 "1"을 지움) |

### 실시간 메시지를 바꿀 때

실시간 메시지는 자동 생성되지 않습니다. 경로나 메시지 DTO를 바꾸면 **이 문서도 같은 PR에서 고쳐 주세요.**
관련 코드: `StompAuthChannelInterceptor`(구독 허용 경로), `LiveChatController`, `*Message`·`*Event`·`ModerationAlert` DTO.
