# 땅땅땅 코드 리뷰 기준

PR 자동 리뷰(`.github/workflows/claude-code-review.yml`)가 이 문서를 기준으로 리뷰합니다.

## 리뷰 방식

- 댓글과 요약은 **한국어**로, 간결하게, 근거(코드 위치·재현 상황)를 들어 씁니다.
- 실제로 문제가 되는 것(버그, 보안, 동시성, 데이터 정합성)을 먼저 봅니다. 취향 차이인 스타일 지적은 하지 않습니다.
- 확실하지 않은 지적은 "확인 필요"라고 밝히고, 왜 의심되는지 적습니다.

## 프로젝트 전제

- 라이브 커머스 경매 서비스입니다. **방송과 경매는 별개 도메인**입니다. 방송은 경매를 보여 주기만 하고, 방송 종료가 경매를 끝내거나 막지 않습니다.
- 프론트엔드는 **PC 웹 전용**(최소 폭 1280px)입니다. 모바일 대응이 없다는 지적은 하지 않습니다.
- 프론트엔드 코드는 `frontend/CLAUDE.md` 규칙(디자인 토큰만 사용, `@/api`로만 호출, 세 가지 상태 화면, 접근성)을 따르는지 봅니다.

## 경로별로 볼 것

### 컨트롤러 (`src/main/java/**/controller/**`)
- 요청 DTO 검증(`@Valid`, `@Size` 등)과 알맞은 HTTP 상태 코드
- 비즈니스 로직이 컨트롤러에 들어가 있지 않은지

### 서비스 (`src/main/java/**/service/**`)
- 트랜잭션 경계(`@Transactional`, `readOnly`)가 알맞은지
- N+1 쿼리, 동시성 문제(포인트·도네이션 차감, 입찰 등 race condition), 잠그는 순서
- 트랜잭션 안에서 외부 API를 호출하며 락이나 DB 연결을 오래 잡지 않는지
- 예외가 공통 에러 코드 체계(`BusinessException` + 도메인 `ErrorCode`)를 따르는지

### 실시간 (`src/main/java/**/domain/livechat/**`, `global/websocket/**`)
- STOMP 인증·인가 누락, 구독 경로 권한 검증
- 이벤트 리스너가 커밋 뒤에 실행되는지(`@TransactionalEventListener`)

### 인증 (`src/main/java/**/domain/auth/**`)
- 토큰 검증, 쿠키 속성(HttpOnly, Secure, SameSite), 민감 정보 로깅

### 공통 (`src/main/java/**/global/**`)
- 보안 설정(`permitAll` 범위 등) 변경이 의도치 않게 API를 노출하지 않는지

### 테스트 (`src/test/**`)
- 실제 동작을 검증하는지, 경계·실패 케이스를 포함하는지

### 설정 (`**/*.yml`)
- 비밀번호, 시크릿 키 같은 민감 정보가 하드코딩되어 있지 않은지
- 로컬 개발용 `src/main/resources/application.yml` 변경(로컬 DB 계정 등)이 섞여 들어오지 않았는지

## 리뷰하지 않는 것

- `build/`, `.gradle/`, `.idea/`, `gradle/wrapper/`, `frontend/src/api/generated/`(명세에서 자동 생성)
