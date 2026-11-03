-- 배송지 입력 기한 72시간, 미입력 위약금 10% (feat/order-address-penalty)
--
-- 1) 컬럼: ddl-auto가 update면 새 버전이 뜰 때 자동으로 생기고 기존 주문은 0이 되므로 건너뛴다.
--    validate·none이면 배포 전에 실행한다.
-- 2) 기한 연장: ddl-auto와 상관없이 배포 직후 아래 UPDATE를 한 번 실행한다.
--    실행 전까지 기존 주문은 예전 기한(24시간)으로 취소될 수 있으니 미루지 않는다.
--    기존 주문은 낙찰 때 위약금을 고지하지 않았으므로 위약금률 0을 유지해 전액 환불한다.

ALTER TABLE orders
    ADD COLUMN penalty_rate_bps int NOT NULL DEFAULT 0;

UPDATE orders
SET address_deadline_at = GREATEST(address_deadline_at, DATE_ADD(created_at, INTERVAL 72 HOUR))
WHERE status = 'ESCROW' AND address IS NULL AND address_deadline_at IS NOT NULL;
