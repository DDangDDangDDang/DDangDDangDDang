-- 자동 구매 확정 실패 횟수 (feat/order-auto-confirm)
--
-- ddl-auto가 update면 새 버전이 뜰 때 자동으로 생기므로 건너뛴다. validate·none이면 배포전에 실행한다.
-- 기존 주문은 0으로 채워진다

ALTER TABLE orders
    ADD COLUMN auto_confirm_fail_count int NOT NULL DEFAULT 0;