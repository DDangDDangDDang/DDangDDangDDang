-- 자동 구매 확정 실패 횟수·마지막 실패 시각, 조회용 인덱스 (feat/order-auto-confirm)
--
-- ddl-auto가 update면 새 버전이 뜰 때 자동으로 생기므로 건너뛴다. validate·none이면 배포 전에 실행한다.
-- 기존 주문은 실패 횟수 0, 마지막 실패 시각 NULL로 채워진다.

ALTER TABLE orders
    ADD COLUMN auto_confirm_fail_count int NOT NULL DEFAULT 0,
    ADD COLUMN auto_confirm_last_failed_at datetime(6) NULL;

CREATE INDEX idx_orders_status_shipped_at ON orders (status, shipped_at);

-- 자동 확정에서 제외된 주문 확인 (관리자)
-- SELECT id, auto_confirm_fail_count, auto_confirm_last_failed_at FROM orders
-- WHERE status = 'SHIPPING' AND auto_confirm_fail_count >= 10;

-- 원인을 고친 뒤 다시 자동 확정 대상으로 되돌리기
-- UPDATE orders SET auto_confirm_fail_count = 0, auto_confirm_last_failed_at = NULL WHERE id = ?;