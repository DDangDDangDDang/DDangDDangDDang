-- 도네이션 마일리지 하나로 합치기 (feat/mileage-unify)
--
-- 받은 마일리지와 적립 마일리지를 하나의 잔액으로 합친다. 기존 '받은 마일리지'는 회원별 적립 묶음 하나로 옮기고,
-- 이 SQL을 실행한 때부터 유효기간(정책의 point_valid_days, 기본 90일)을 준다.
--
-- 실행 순서 (한 번만):
--   1. 서버 중지
--   2. 이 SQL 실행
--      docker exec -i ddang-mysql mysql --default-character-set=utf8mb4 -uddang -pddang ddang < docs/migrations/2026-10-28-mileage-unify.sql
--   3. 새 버전 서버 기동
-- ddl-auto(update)는 컬럼 삭제·타입 변경을 하지 않으므로, 이 SQL 없이 새 버전을 띄우면 지갑 만들기·선물이 실패한다.
-- 기존 이력의 type 컬럼은 MySQL ENUM이라 새 값(SALE)을 넣을 수 없어 문자열로 바꾼다.

SET NAMES utf8mb4;

SET @valid_days = COALESCE((SELECT point_valid_days FROM donation_policies ORDER BY id DESC LIMIT 1), 90);

-- 1. 적립 묶음: 출처(source)와 선물 ID를 더하고, 주문·판매자는 비어 있을 수 있게 한다. (선물받음 묶음)
ALTER TABLE donation_point_lots
    ADD COLUMN source varchar(20) NOT NULL DEFAULT 'PURCHASE' AFTER user_id,
    ADD COLUMN donation_id bigint NULL AFTER order_id,
    MODIFY order_id bigint NULL,
    MODIFY seller_id bigint NULL;
ALTER TABLE donation_point_lots ALTER COLUMN source DROP DEFAULT;

-- order_id 하나에 걸린 유니크(이름이 자동 생성됨)를 지우고 (order_id, source) 유니크로 바꾼다. (같은 주문으로 구매·판매 적립 한 번씩)
SET @old_uk = (SELECT index_name FROM information_schema.statistics
               WHERE table_schema = DATABASE() AND table_name = 'donation_point_lots'
                 AND column_name = 'order_id' AND non_unique = 0 AND seq_in_index = 1
                 AND index_name NOT IN ('uk_donation_lot_order_source')
               LIMIT 1);
SET @sql = IF(@old_uk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE donation_point_lots DROP INDEX `', @old_uk, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
ALTER TABLE donation_point_lots
    ADD CONSTRAINT uk_donation_lot_order_source UNIQUE (order_id, source),
    ADD CONSTRAINT uk_donation_lot_donation UNIQUE (donation_id);

-- 2. 묶음 사용 기록: 선물뿐 아니라 상점 구매에도 쓰도록 (type, ref_id)로 바꾼다.
ALTER TABLE donation_lot_usages
    ADD COLUMN type varchar(20) NOT NULL DEFAULT 'GIFT',
    ADD COLUMN ref_id bigint NULL;
UPDATE donation_lot_usages SET ref_id = donation_id;
ALTER TABLE donation_lot_usages
    MODIFY ref_id bigint NOT NULL,
    ALTER COLUMN type DROP DEFAULT,
    DROP INDEX idx_donation_lot_usage_donation,
    DROP COLUMN donation_id,
    ADD INDEX idx_donation_lot_usage_ref (type, ref_id);

-- 3. 지갑: 잔액 하나로. 받은 마일리지는 묶음으로 옮긴다.
ALTER TABLE donation_wallets ADD COLUMN balance bigint NOT NULL DEFAULT 0;
INSERT INTO donation_point_lots (created_at, updated_at, user_id, source, order_id, donation_id, seller_id,
                                 amount, remaining, earned_at, expires_at, expiry_notified)
SELECT NOW(6), NOW(6), user_id, 'RECEIVE', NULL, NULL, NULL,
       received_balance, received_balance, NOW(6), NOW(6) + INTERVAL @valid_days DAY, 0
FROM donation_wallets
WHERE received_balance > 0;
UPDATE donation_wallets SET balance = giftable_balance + received_balance;
ALTER TABLE donation_wallets
    ALTER COLUMN balance DROP DEFAULT,
    DROP COLUMN giftable_balance,
    DROP COLUMN received_balance;

-- 4. 내역: 변동 뒤 잔액 하나로, type은 문자열로.
ALTER TABLE donation_point_histories
    ADD COLUMN balance_after bigint NOT NULL DEFAULT 0,
    MODIFY type varchar(20) NOT NULL;
UPDATE donation_point_histories SET balance_after = giftable_after + received_after;
ALTER TABLE donation_point_histories
    ALTER COLUMN balance_after DROP DEFAULT,
    DROP COLUMN giftable_after,
    DROP COLUMN received_after;

-- 확인: 지갑 잔액 = 남은 묶음 합계 (diff가 0이어야 한다)
SELECT w.user_id, w.balance,
       COALESCE((SELECT SUM(l.remaining) FROM donation_point_lots l WHERE l.user_id = w.user_id), 0) AS lots,
       w.balance - COALESCE((SELECT SUM(l.remaining) FROM donation_point_lots l WHERE l.user_id = w.user_id), 0) AS diff
FROM donation_wallets w
ORDER BY w.user_id;
