-- 주문 배송 기한 · 자동 취소 · 발송 지연 신청 (feat/order-delivery-deadline)
--
-- 1) 컬럼·인덱스: ddl-auto가 update면 새 버전이 뜰 때 자동으로 생기므로 건너뛴다. validate·none이면 배포 전에 실행한다.
-- 2) 기한 채우기: ddl-auto와 상관없이 배포 뒤 아래 UPDATE 두 문장만 한 번 실행한다.
--    진행 중인 주문은 실행 시점부터 기한을 새로 시작한다. 실행 전에는 기한이 NULL이라 자동 취소되지 않는다.
--    앱이 Asia/Seoul 시각을 저장하므로 같은 기준으로 넣는다.

ALTER TABLE orders
    ADD COLUMN address_deadline_at datetime(6) NULL,
    ADD COLUMN ship_deadline_at datetime(6) NULL,
    ADD COLUMN delay_status varchar(20) NULL,
    ADD COLUMN delay_reason varchar(200) NULL,
    ADD COLUMN delay_requested_at datetime(6) NULL,
    ADD COLUMN delay_responded_at datetime(6) NULL,
    ADD COLUMN cancel_reason varchar(30) NULL,
    ADD COLUMN canceled_at datetime(6) NULL;

CREATE INDEX idx_orders_status_address_deadline ON orders (status, address_deadline_at);
CREATE INDEX idx_orders_status_ship_deadline ON orders (status, ship_deadline_at);

UPDATE orders
SET address_deadline_at = DATE_ADD(CONVERT_TZ(UTC_TIMESTAMP(6), '+00:00', '+09:00'), INTERVAL 24 HOUR)
WHERE status = 'ESCROW' AND address IS NULL AND address_deadline_at IS NULL;

UPDATE orders
SET ship_deadline_at = DATE_ADD(CONVERT_TZ(UTC_TIMESTAMP(6), '+00:00', '+09:00'), INTERVAL 7 DAY)
WHERE status = 'ESCROW' AND address IS NOT NULL AND ship_deadline_at IS NULL;
