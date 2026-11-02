# 주문 배송 기한 · 자동 취소 · 발송 지연 신청

- 작성일: 2026-11-02
- 범위: 백엔드(order, point, auction, product, directmessage, notification), 프론트엔드(주문, 경매 이력)

## 목표

낙찰 뒤 거래가 멈춘 주문을 사람이 개입하지 않아도 끝낸다.

- 구매자가 낙찰 후 24시간 안에 배송지를 넣지 않으면 낙찰을 취소한다.
- 판매자가 배송지 입력 후 7일 안에 발송하지 않으면 판매를 취소한다.
- 판매자는 발송 지연을 신청할 수 있고, 구매자가 승인하면 기한이 7일 늘어난다.

## 확정한 정책

| 항목 | 정책 |
|---|---|
| 낙찰취소 | 구매자에게 전액 환불, 위약금 없음 |
| 제품 미발송 | 구매자에게 전액 환불 |
| 발송 기한 시작 | 구매자가 배송지를 처음 입력한 시각. 고쳐도 기한은 그대로 |
| 지연 신청 | 주문당 1회, 사유 필수, 재신청 불가 |
| 구매자 응답 | 승인하면 현재 기한 +7일, 거절·무응답이면 기존 기한 유지 |
| 응답 가능 시점 | 발송 기한 전까지 |
| 취소 후 상품 | 판매자가 같은 상품을 다시 경매에 올릴 수 있음 |
| 위약금 · 입찰 제한 | 이번 범위 밖, 후속 과제 |

## 상태

```text
ESCROW ─ 낙찰 후 24시간 내 배송지 미입력 ──→ CANCELED (BUYER_NO_ADDRESS)
   │ 배송지 입력
   ├─ 기한 내 미발송 ─────────────────────→ CANCELED (SELLER_NOT_SHIPPED)
   └─ 송장 입력 → SHIPPING → COMPLETED
```

## 데이터

`orders`에 모두 nullable로 추가한다.

| 컬럼 | 설명 |
|---|---|
| `address_deadline_at` | 주문 생성 시각 +24시간 |
| `ship_deadline_at` | 첫 배송지 입력 시각 +7일, 승인 시 +7일 |
| `delay_status` | `PENDING`, `APPROVED`, `REJECTED`, 신청 전 NULL |
| `delay_reason` | 지연 사유 (최대 200자) |
| `delay_requested_at` | 신청 시각 |
| `delay_responded_at` | 구매자 응답 시각 |
| `cancel_reason` | `BUYER_NO_ADDRESS`, `SELLER_NOT_SHIPPED` |
| `canceled_at` | 취소 시각 |

- `OrderStatus`에 `CANCELED`, `PointHistoryType`에 `ORDER_REFUND`를 추가한다. 프로젝트 관례대로 varchar로 저장한다.
- 스케줄러 조회용으로 `(status, address_deadline_at)`, `(status, ship_deadline_at)` 인덱스를 둔다.
- `docs/migrations/2026-11-02-order-delivery-deadline.sql`을 추가한다.
  - 진행 중인 ESCROW 주문은 배포 시점부터 기한을 새로 시작한다.
  - 배송지 없는 주문은 `address_deadline_at`, 있는 주문은 `ship_deadline_at`을 채운다.
  - 기한 컬럼이 NULL이면 스케줄러가 취소하지 않으므로, 마이그레이션 전에도 대량 취소는 일어나지 않는다.
  - 앱의 `LocalDateTime` 기준 시간대와 맞춘 값을 넣는다.

## 동작

### 자동 취소

- `OrderDeadlineScheduler`가 1분마다 기한이 지난 주문 ID를 배치로 조회한다.
- 건별로 별도 트랜잭션에서 주문을 잠그고 상태와 기한을 다시 확인한 뒤 취소한다.
  한 건이 실패해도 나머지는 계속 처리하고, 실패 건은 다음 주기에 다시 시도한다.
- 잠금 순서는 구매 확정과 같이 주문 → 지갑이다. 상태를 먼저 CANCELED로 바꾸므로 환불은 한 번만 된다.
- `@Scheduled` 작업이 9개가 되므로 `SchedulingConfig` 기본 풀 크기와 주석을 9로 바꾼다.

### 기한 경계

- 기한이 지나면 스케줄러가 돌기 전이어도 배송지 입력, 발송, 지연 신청, 지연 응답을 거부한다.
- 기한이 NULL인 주문은 기한 검사를 하지 않는다.

### 환불과 후속 처리

- `PointService.refundOrder(buyerId, amount, orderId)`가 잔액을 늘리고 `ORDER_REFUND`(참조 ORDER)를 남긴다.
- 정산 전에 취소되므로 판매자 정산금, 수수료, 마일리지는 건드릴 것이 없다.
- `OrderCanceledEvent`를 발행한다.
  - 경매 도메인이 BEFORE_COMMIT으로 받아 상품을 `SOLD → ACTIVE`로 되돌린다. 삭제된 상품은 그대로 둔다.
  - 경매 상태는 `ENDED`로 유지한다.
  - 메신저와 알림은 AFTER_COMMIT으로 처리하고, 실패해도 주문 취소는 되돌리지 않는다.
- 낙찰 건수 통계 `soldAuctionCount`는 이번에 바꾸지 않는다. 파워 판매자 실적은 COMPLETED만 세므로 자동으로 제외된다.

### 지연 신청 API

기존 `OrderController` 경로를 따른다.

| 메서드 | 경로 | 권한 |
|---|---|---|
| POST | `/orders/{orderId}/delay-request` | 판매자 |
| POST | `/orders/{orderId}/delay-request/approve` | 구매자 |
| POST | `/orders/{orderId}/delay-request/reject` | 구매자 |

- 신청 조건: ESCROW, 배송지 입력됨, 기한 전, 이전 신청 없음.
- 응답 조건: `delay_status = PENDING`, 기한 전.

### 메신저

- `DirectMessageService`에 서버 전용 발송 메서드를 추가한다. 차단, 도배 제한, 내용 필터를 적용하지 않는다.
- 발신자는 판매자, 수신자는 구매자로 두고 기존 저장 · STOMP 전달 경로를 그대로 쓴다.
- 승인·거절 결과만 발신자가 구매자, 수신자가 판매자다.
- 지연 사유는 신청 API에서 내용 필터를 적용한다. 서버 전용 발송은 필터를 다시 적용하지 않는다.
- 재시도해도 한 번만 저장되도록 `clientMessageId`는 `order-delay-{orderId}`처럼 고정한다.
- 모든 메시지는 `[경매 #{auctionId} · {상품명}]`으로 시작한다.

```text
[경매 #123 · 상품명] 판매자가 발송 지연을 신청했어요.
사유: …
승인하면 발송 기한이 7일 늘어납니다. (주문 상세에서 승인/거절)
```

- 구매자의 승인·거절 결과도 같은 형식으로 보낸다.

### 알림

- `NotificationType`에 `ORDER_CANCELED`를 추가해 구매자와 판매자에게 보낸다.
- 기한 임박 사전 알림은 범위 밖이다.

### 경매 기록과 화면

- 판매자 본인 경매 이력 응답에 `orderCancelReason`을 추가하고, `낙찰취소`, `제품 미발송`으로 표시한다.
- 주문 상세: 남은 기한, 지연 신청(판매자), 승인·거절(구매자), 취소 사유.
- 주문 목록과 포인트 내역: `취소됨`, `주문 취소 환불` 라벨.
- `docs/api/openapi.json`과 `docs/api/README.md`를 갱신한다.

## 테스트

- 단위: 주문 상태 전이, 기한 경계, 지연 신청 조건.
- 통합(Testcontainers MySQL):
  - 기한 경과 취소, 환불 원장, 상품 ACTIVE 복귀 후 재등록.
  - 발송과 취소 동시 실행 시 환불 1회.
  - 스케줄러 중복 실행 시 환불 1회.
  - 승인 시 기한 7일 연장, 거절·무응답 시 기존 기한 취소.
  - 차단 상태에서도 지연 안내 메시지 저장.
- 프론트엔드: 주문 상세의 역할별 버튼과 라벨 테스트, `npm run check`.

## 배포 시 주의

- `docs/migrations` SQL을 dev와 운영에 실행해야 기존 ESCROW 주문에 기한이 적용된다.
- 메신저는 윤정민, 경매 기록은 조성연 담당이므로 PR 전에 공유한다.
- 머지 후 발표자료 41장의 "주문 취소 · 환불"을 향후 계획에서 뺀다.
