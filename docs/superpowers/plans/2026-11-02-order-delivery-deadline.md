# 주문 배송 기한 · 자동 취소 · 발송 지연 신청 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기한을 넘긴 주문을 자동 취소·환불하고, 판매자의 발송 지연 신청과 구매자 승인을 지원한다.

**Architecture:** `Order`가 기한과 전이 규칙을 갖고, `OrderService`가 잠금·환불·이벤트 발행을 맡으며, `OrderDeadlineScheduler`는 대상 주문만 찾는다. 상품 복귀(BEFORE_COMMIT)와 메신저·알림(커밋 후 비동기)은 `OrderCanceledEvent` 등을 받아 처리한다.

**Tech Stack:** Spring Boot, JPA, MySQL(Testcontainers), React 19, TanStack Query.

**Spec:** `docs/superpowers/specs/2026-11-02-order-delivery-deadline-design.md`

## Global Constraints

- 배송지 기한 24시간, 발송 기한 7일, 승인 연장 7일, 지연 신청 주문당 1회, 사유 최대 200자.
- 취소 시 구매자에게 `finalPrice` 전액 환불, 위약금 없음.
- 잠금 순서는 주문 → 지갑.
- 기한이 NULL이면 기한 검사도 자동 취소도 하지 않는다.
- 메시지 첫머리는 정확히 `[경매 #{auctionId} · {상품명}]`.
- 주석과 UI 문구는 한국어. 프론트엔드는 `npm run check` 통과.
- 커밋 단계는 사용자가 커밋을 승인한 경우에만 수행한다.
- 시각을 비교하는 엔티티 메서드는 테스트할 수 있게 `LocalDateTime now`를 인자로 받는다.
- 경로 prefix는 모두 `src/main/java/com/example/ddangddangddang/domain` (D), `src/test/java/com/example/ddangddangddang/domain` (T).

## Review Focus

1. 지연 사유는 차단·필터 없는 서버 경로로 전달되므로, 전화번호·계좌 같은 외부 거래 유도 문구가 사유로 새면 안 된다. 신청 시 내용 필터를 적용해 거부한다. (Task 4)
2. 구매자 지갑이 없어 환불이 실패해도 다른 주문의 취소는 계속되고, 실패 주문은 ESCROW로 남아 다음 주기에 재시도된다. (Task 3)
3. 승인 후 배송지를 고쳐도 연장된 기한이 초기화되지 않는다. (Task 1)
4. 기한이 지난 뒤 스케줄러가 돌기 전의 승인 요청은 거부되어 취소를 뒤집지 못한다. (Task 4)
5. 판매자가 상품을 삭제한 뒤 주문이 취소돼도 상품이 되살아나지 않는다. (Task 3)

---

### Task 1: 주문 상태·기한 규칙과 마이그레이션

**Files:**
- Create: `D/order/entity/OrderCancelReason.java`, `D/order/entity/OrderDelayStatus.java`, `docs/migrations/2026-11-02-order-delivery-deadline.sql`
- Modify: `D/order/entity/OrderStatus.java`, `D/order/entity/Order.java`, `D/order/exception/OrderErrorCode.java`, `D/order/dto/OrderResponse.java`, `D/order/dto/OrderSummaryResponse.java`
- Test: `T/order/entity/OrderTest.java`

**Interfaces:**
- Produces:
  - `OrderStatus.CANCELED`, `OrderCancelReason { BUYER_NO_ADDRESS, SELLER_NOT_SHIPPED }`, `OrderDelayStatus { PENDING, APPROVED, REJECTED }`
  - `Order.registerDeliveryAddress(String, String, String, String, LocalDateTime now)`, `Order.ship(Courier, String, LocalDateTime now)`
  - `boolean Order.isAddressOverdue(LocalDateTime now)`, `boolean Order.isShipOverdue(LocalDateTime now)`
  - `void Order.cancel(OrderCancelReason reason, LocalDateTime now)`
  - `void Order.requestDelay(String reason, LocalDateTime now)`, `void Order.approveDelay(LocalDateTime now)`, `void Order.rejectDelay(LocalDateTime now)`
  - 오류 코드 `DEADLINE_PASSED`(ORDER_017), `CANNOT_REQUEST_DELAY`(ORDER_018), `CANNOT_RESPOND_DELAY`(ORDER_019), `NOT_ORDER_BUYER_DELAY`(ORDER_020), `NOT_ORDER_SELLER_DELAY`(ORDER_021), `DELAY_REASON_NOT_ALLOWED`(ORDER_022). 앞의 셋은 409, NOT_ORDER_* 둘은 403, 마지막은 400.

- [ ] **Step 1: 실패 테스트 작성** — `OrderTest`에 추가:
  - `create_setsAddressDeadline24HoursLater`: `addressDeadlineAt`이 생성 시각 +24시간.
  - `registerAddress_firstTime_setsShipDeadline`: `now + 7일`.
  - `registerAddress_afterDelayApproved_keepsExtendedDeadline`: 입력 → 신청 → 승인 → 수정 뒤 기한이 첫 입력 +14일.
  - `registerAddress_afterDeadline_throwsDeadlinePassed`, `ship_afterDeadline_throwsDeadlinePassed`.
  - `requestDelay_twice_throwsCannotRequestDelay`, `requestDelay_withoutAddress_throwsCannotRequestDelay`.
  - `approveDelay_extendsCurrentDeadlineBy7Days`, `rejectDelay_keepsDeadline`.
  - `approveDelay_afterDeadline_throwsDeadlinePassed`.
  - `cancel_setsReasonAndCanceledAt`, `cancel_whenShipping_throwsIllegalState`: 잘못된 호출이므로 `IllegalStateException`.
  - `nullDeadlines_notOverdue`.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*order.entity.OrderTest'` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 스펙 데이터 표의 컬럼과 enum, 위 메서드를 추가한다. 기존 `ship`/`registerDeliveryAddress` 호출부는 `LocalDateTime.now()`를 넘긴다. `OrderResponse`에 `cancelReason`, `canceledAt`, `addressDeadlineAt`, `shipDeadlineAt`, `delayStatus`, `delayReason`을, `OrderSummaryResponse`에 `cancelReason`을 추가한다. `@Table` 인덱스 `idx_orders_status_address_deadline`, `idx_orders_status_ship_deadline`을 선언한다.
- [ ] **Step 4: 마이그레이션 작성** — 스펙 규칙대로 컬럼·인덱스 추가 후 ESCROW 주문 기한을 채운다. `NOW()` 대신 `@now := CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+09:00')` 값을 쓴다. 단, `application.yml`의 시간대 설정을 먼저 확인해 다르면 그 값에 맞춘다.
- [ ] **Step 5: 통과 확인** — Run: `./gradlew test --tests '*order*'` / Expected: PASS. 기존 테스트의 시그니처는 함께 수정한다.
- [ ] **Step 6: 커밋** — `feat : 주문 배송 기한과 지연 신청 상태 규칙 추가`

### Task 2: 주문 취소 환불 원장

**Files:**
- Modify: `D/point/entity/PointHistoryType.java`, `D/point/entity/PointWallet.java`, `D/point/service/PointService.java`
- Test: `T/point/entity/PointWalletTest.java`, `T/point/service/PointServiceTest.java`(없으면 생성)

**Interfaces:**
- Produces: `PointHistoryType.ORDER_REFUND`, `void PointWallet.refundOrder(long amount)`, `void PointService.refundOrder(Long buyerId, long amount, Long orderId)`

- [ ] **Step 1: 실패 테스트 작성** — `refundOrder_increasesBalanceOnly`: 잔액 +amount, `heldBalance` 불변. `refundOrder_recordsOrderRefundHistory`: 유형 `ORDER_REFUND`, 참조 `ORDER`, `orderId`.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*PointWalletTest'` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — `refundExchange`, `settle`과 같은 패턴으로 추가한다.
- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests '*point*'` / Expected: PASS.
- [ ] **Step 5: 커밋** — `feat : 주문 취소 환불 포인트 원장 추가`

### Task 3: 자동 취소 스케줄러와 상품 복귀

**Files:**
- Create: `D/order/service/OrderDeadlineScheduler.java`, `D/order/event/OrderCanceledEvent.java`(기존 `OrderConfirmedEvent`와 같은 패키지), `D/auction/event/OrderCancelProductListener.java`
- Modify: `D/order/service/OrderService.java`, `D/order/repository/OrderRepository.java`, `D/product/entity/Product.java`, `global/config/SchedulingConfig.java`
- Test: `T/order/service/OrderDeadlineCancelTest.java`(`MySqlIntegrationTest` 상속)

**Interfaces:**
- Consumes: Task 1 `Order.cancel`, Task 2 `PointService.refundOrder`
- Produces:
  - `record OrderCanceledEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId, String productName, OrderCancelReason reason)`
  - `boolean OrderService.cancelOverdue(Long orderId, OrderCancelReason reason)`: 주문 잠금 후 ESCROW와 기한 초과를 재확인, 아니면 false
  - `List<Long> OrderRepository.findAddressOverdueIds(LocalDateTime now, Pageable)`: ESCROW, `address IS NULL`, 기한 경과
  - `List<Long> OrderRepository.findShipOverdueIds(LocalDateTime now, Pageable)`: ESCROW, 기한 경과
  - `void Product.relist()`: `SOLD`일 때만 `ACTIVE`

- [ ] **Step 1: 실패 테스트 작성**
  - `addressOverdue_cancelsAndRefundsFullPrice`, `shipOverdue_cancelsWithSellerNotShipped`.
  - `cancel_restoresProductToActive_andCanReopenAuction`: 경매 등록이 `PRODUCT_NOT_AVAILABLE` 없이 성공.
  - `cancel_keepsDeletedProductDeleted`.
  - `cancelOverdue_twiceConcurrently_refundsOnce`, `shipAndCancelConcurrently_endsInOneState`: `ORDER_REFUND` 건수는 최대 1.
  - `addressEnteredBeforeDeadline_notCancelledByAddressQuery`.
  - `missingBuyerWallet_failsOneOrder_othersStillCancelled`: 실패 주문은 ESCROW 유지.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*OrderDeadlineCancelTest'` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — `OrderDeadlineScheduler`는 `PointExchangeScheduler`처럼 건별 try/catch로 처리한다. `@Scheduled(fixedDelayString = "${order.deadline.delay-ms:60000}")`, 배치 100건. 리스너는 `AuctionSettlementListener`처럼 BEFORE_COMMIT으로 `auctionId`의 상품을 잠가 `relist()`한다. `SchedulingConfig` 기본값과 주석을 9로 바꾼다.
- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests '*OrderDeadlineCancelTest' --tests '*OrderConcurrencyTest'` / Expected: PASS.
- [ ] **Step 5: 커밋** — `feat : 기한 초과 주문 자동 취소와 환불 처리`

### Task 4: 지연 신청 API

**Files:**
- Create: `D/order/dto/OrderDelayRequest.java`, `D/order/event/OrderDelayRequestedEvent.java`, `D/order/event/OrderDelayRespondedEvent.java`
- Modify: `D/order/service/OrderService.java`, `D/order/controller/OrderController.java`
- Test: `T/order/service/OrderServiceTest.java`, `T/order/controller/` 의 주문 컨트롤러 테스트

**Interfaces:**
- Consumes: Task 1 `requestDelay`, `approveDelay`, `rejectDelay`
- Produces:
  - `record OrderDelayRequest(@NotBlank @Size(max = 200) String reason)`
  - `OrderResponse OrderService.requestDelay(Long userId, Long orderId, String reason)`, `approveDelay(Long userId, Long orderId)`, `rejectDelay(Long userId, Long orderId)`
  - `record OrderDelayRequestedEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId, String productName, String reason)`
  - `record OrderDelayRespondedEvent(Long orderId, Long auctionId, Long buyerId, Long sellerId, String productName, boolean approved)`
  - `POST /api/v1/orders/{orderId}/delay-request`, `.../approve`, `.../reject`

- [ ] **Step 1: 실패 테스트 작성** — `requestDelay_bySeller_setsPendingAndPublishesEvent`, `requestDelay_byBuyer_throwsNotOrderSellerDelay`, `requestDelay_contactInReason_throwsDelayReasonNotAllowed`, `approveDelay_bySeller_throwsNotOrderBuyerDelay`, `approveDelay_afterDeadline_throwsDeadlinePassed`, `rejectDelay_publishesEventWithApprovedFalse`, 컨트롤러 `delayRequest_blankReason_returns400`.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*OrderServiceTest'` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 모두 `findOrderForUpdate`로 잠근다. 사유 필터는 `DirectMessageService`의 `contentFilter`와 같은 빈의 `findViolation`을 쓰고, 위반이면 `DELAY_REASON_NOT_ALLOWED`로 거부한다.
- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests '*order*'` / Expected: PASS.
- [ ] **Step 5: 커밋** — `feat : 판매자 발송 지연 신청과 구매자 승인 API 추가`

### Task 5: 메신저 안내

**Files:**
- Create: `D/order/event/OrderDmNotifier.java`
- Modify: `D/directmessage/service/DirectMessageService.java`
- Test: `T/directmessage/service/DirectMessageSystemSendTest.java`, `T/order/event/OrderDmNotifierTest.java`

**Interfaces:**
- Consumes: Task 3·4 이벤트
- Produces: `void DirectMessageService.sendOrderNotice(Long senderId, Long receiverId, String clientMessageId, String content)`

- [ ] **Step 1: 실패 테스트 작성**
  - `sendOrderNotice_savesEvenWhenBlocked`, `sendOrderNotice_ignoresRateLimit`, `sendOrderNotice_sameClientMessageId_savesOnce`, `sendOrderNotice_createsRoomWhenMissing`.
  - `delayRequested_messageStartsWithAuctionLabel`: `[경매 #123 · 빈티지 시계] 판매자가 발송 지연을 신청했어요.`로 시작하고 사유를 포함.
  - `delayApproved_buyerSendsResultToSeller`, `delayRejected_messageSaysDeadlineKept`, `orderCanceled_messageStatesReason`.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*DirectMessageSystemSendTest'` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — `sendOrderNotice`는 `getOrCreateRoom` 후 저장·방 갱신·`deliver`만 하며 `dmModerationQueue`는 건너뛴다. `OrderDmNotifier`는 `@Async @TransactionalEventListener`로 받고 실패는 경고 로그만 남긴다. `clientMessageId`는 `order-delay-request-{orderId}`, `order-delay-response-{orderId}`, `order-cancel-{orderId}`. 지연 신청과 취소 안내의 발신자는 판매자, 승인·거절 결과의 발신자는 구매자다.
- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests '*directmessage*' --tests '*OrderDmNotifierTest'` / Expected: PASS.
- [ ] **Step 5: 커밋** — `feat : 지연 신청과 주문 취소를 메신저로 안내`

### Task 6: 취소 알림

**Files:**
- Create: `D/notification/event/OrderCanceledNotifier.java`
- Modify: `D/notification/entity/NotificationType.java`, `D/notification/entity/NotificationCategory.java`, `D/notification/entity/NotificationContent.java`, `frontend/src/components/notification/NotificationIcon.tsx`
- Test: `T/notification/event/OrderCanceledNotifierTest.java`

**Interfaces:**
- Consumes: Task 3 `OrderCanceledEvent`, `Notification.auction(userId, type, auctionId, productName, price)`, `NotificationDispatcher.dispatch(List<Notification>)`
- Produces: `NotificationType.ORDER_CANCELED`

- [ ] **Step 1: 실패 테스트 작성** — `orderCanceled_dispatchesToBuyerAndSeller`, `orderCanceled_contentLinksToOrders`: 제목 `주문이 취소됐어요`, 본문 `‘상품명’ 주문이 기한 초과로 취소됐어요. 결제 포인트는 구매자에게 전액 환불돼요.`, 링크 `/orders`.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*OrderCanceledNotifierTest'` / Expected: 컴파일 실패.
- [ ] **Step 3: 구현** — 스키마 변경 없이 `AUCTION_RESULT` 묶음에 넣는다. 아이콘은 `AUCTION_CANCELED`와 같게 둔다. 알림함의 링크 라우트를 `frontend/src` 라우터에서 확인해 일치시킨다.
- [ ] **Step 4: 통과 확인** — Run: `./gradlew test --tests '*notification*'` / Expected: PASS.
- [ ] **Step 5: 커밋** — `feat : 주문 취소 알림 추가`

### Task 7: 경매 이력 응답과 API 문서

**Files:**
- Modify: `D/auction/dto/response/AuctionResponse.java`, `D/auction/service/AuctionService.java`, `D/order/repository/OrderRepository.java`, `docs/api/openapi.json`, `docs/api/README.md`
- Test: `T/auction/service/` 의 경매 이력 테스트

**Interfaces:**
- Consumes: Task 1 `OrderCancelReason`
- Produces: `AuctionResponse.orderCancelReason`(nullable), `AuctionResponse withOrderCancelReason(OrderCancelReason)`, `List<Order> OrderRepository.findByAuctionIdInAndStatus(Collection<Long>, OrderStatus)`

- [ ] **Step 1: 실패 테스트 작성** — `productAuctionHistory_includesOrderCancelReason`, `productAuctionHistory_noCancelledOrder_reasonNull`.
- [ ] **Step 2: 실패 확인** — Run: `./gradlew test --tests '*Auction*History*'` / Expected: FAIL.
- [ ] **Step 3: 구현** — `productAuctionHistory`에서만 채운다. 다른 조회는 null이다. 50건을 한 번에 조회해 N+1을 피한다.
- [ ] **Step 4: 문서 갱신** — Run: `./gradlew test --tests '*OpenApiSpecTest'` / Expected: `openapi.json` 갱신. README에 DM 안내 형식과 신규 API를 적는다.
- [ ] **Step 5: 커밋** — `feat : 경매 이력에 주문 취소 사유 표시 정보 추가`

### Task 8: 프론트엔드

**Files:**
- Create: `frontend/src/pages/orders/OrderDelayCard.tsx`, `frontend/src/pages/orders/OrderDelayCard.test.tsx`
- Modify: `frontend/src/lib/labels.ts`, `frontend/src/lib/order-view.ts`, `frontend/src/lib/order-view.test.ts`, `frontend/src/components/order/OrderBits.tsx`, `frontend/src/pages/orders/OrderDetailPage.tsx`, `frontend/src/pages/orders/OrdersPage.tsx`, `frontend/src/pages/auctions/ProductAuctionHistory.tsx`

**Interfaces:**
- Consumes: Task 1·4·7 응답과 API (`npm run gen:api`로 생성)
- Produces: `OrderStage`에 `'CANCELED'` 추가, `ORDER_CANCEL_REASON_LABEL = { BUYER_NO_ADDRESS: '낙찰취소', SELLER_NOT_SHIPPED: '제품 미발송' }`, `ORDER_STATUS_LABEL.CANCELED = '취소됨'`, 포인트 내역 `ORDER_REFUND: '주문 취소 환불'`(입금으로 표시)

- [ ] **Step 1: 실패 테스트 작성** — `orderStage`가 `CANCELED`를 반환. `OrderDelayCard`:
  - 판매자, 신청 전: 사유 입력과 `지연 신청` 버튼.
  - 구매자, `PENDING`: 사유와 `승인`, `거절` 버튼.
  - 판매자, `PENDING`: `구매자 응답 대기 중`, 버튼 없음.
  - `REJECTED`/`APPROVED`: 재신청 버튼 없음.
  - `SHIPPING`, `CANCELED`: 카드 없음.
- [ ] **Step 2: 실패 확인** — Run: `cd frontend && npm run gen:api && npx vitest run src/lib/order-view.test.ts src/pages/orders/OrderDelayCard.test.tsx` / Expected: FAIL.
- [ ] **Step 3: 구현** — 주문 상세에 단계별 남은 기한(`배송지 입력 기한`, `발송 기한`)과 취소 사유를 표시한다. `ProductAuctionHistory`는 `orderCancelReason`이 있으면 라벨을 배지 옆에 표시한다. 뮤테이션 성공 시 주문 쿼리를 무효화한다.
- [ ] **Step 4: 통과 확인** — Run: `cd frontend && npm run check` / Expected: PASS.
- [ ] **Step 5: 화면 확인** — 로컬 서버에서 판매자·구매자 계정으로 신청, 승인, 취소 라벨을 확인한다.
- [ ] **Step 6: 커밋** — `feat : 주문 기한과 지연 신청 화면 추가`

### Task 9: 전체 검증

- [ ] **Step 1:** Run: `./gradlew test` / Expected: BUILD SUCCESSFUL.
- [ ] **Step 2:** Run: `cd frontend && npm run check` / Expected: PASS.
- [ ] **Step 3:** 스펙의 "배포 시 주의"를 PR 본문에 옮긴다.
