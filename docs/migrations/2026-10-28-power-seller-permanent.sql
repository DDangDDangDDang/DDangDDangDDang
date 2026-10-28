-- 파워 판매자를 영구 아이템으로 바꾸기 (feat/power-seller)
--
-- 1. 지금까지 판매자 상점에서 산 파워 판매자(30일짜리, 끝난 것 포함)를 모두 영구로 바꾼다.
-- 2. 도네이션 정책에서 파워 판매자 가격·기간·라이브 목록 맨 위 칸 수를 없앤다. (가격은 마일리지 상점 상품에 고정: 20,000M)
--
-- 실행 순서 (한 번만):
--   1. 서버 중지
--   2. 이 SQL 실행
--      docker exec -i ddang-mysql mysql --default-character-set=utf8mb4 -uddang -pddang ddang < docs/migrations/2026-10-28-power-seller-permanent.sql
--   3. 새 버전 서버 기동
-- 지우는 정책 컬럼은 NOT NULL이라, 이 SQL 없이 새 버전을 띄우면 관리자가 도네이션 정책을 바꿀 때 저장이 실패한다.
-- (거꾸로 SQL만 먼저 실행하고 예전 버전을 계속 띄우면 예전 버전이 정책을 읽지 못한다)

SET NAMES utf8mb4;

UPDATE seller_perks SET ends_at = '9999-12-31 00:00:00' WHERE type = 'POWER_SELLER';

ALTER TABLE donation_policies
    DROP COLUMN power_seller_price,
    DROP COLUMN power_seller_days,
    DROP COLUMN promoted_live_slots;

-- 확인: 파워 판매자 (모두 9999-12-31까지)
SELECT seller_id, MIN(starts_at) AS since, MAX(ends_at) AS ends_at, COUNT(*) AS purchases
FROM seller_perks WHERE type = 'POWER_SELLER' GROUP BY seller_id;
