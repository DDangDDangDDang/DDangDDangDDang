-- 시나리오 E(마감 몰림)용: 마감 시각이 같은 경매 @count개를 만든다. 판매자 900000(seed.sql), 번호 9100001부터.
-- 시작가 1,000 / 입찰 단위 1,000 / 즉시구매 없음 / 지금 시작, @minutes분 뒤 마감
-- ⚠ 실행하자마자 close-rush.js를 시작한다. k6의 HOLD는 @minutes분보다 짧게 잡아야 입찰이 멈춘 뒤 마감된다
--    예) @minutes = 5, k6 -e HOLD=3m30s → 4분 30초 동안 입찰, 마지막 입찰 30초 뒤 마감 처리
-- 다음 단계를 하기 전에 cleanup-close.sql로 지우고 다시 만든다.

SET @count = 10;
SET @minutes = 5;
SET SESSION cte_max_recursion_depth = 1000;

INSERT INTO products (id, seller_id, category_id, name, description, status, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @count),
     leaf AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn, COUNT(*) OVER () AS cnt
              FROM categories c
              WHERE c.parent_id IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM categories child WHERE child.parent_id = c.id))
SELECT 9100000 + seq.n, 900000, leaf.id, CONCAT('마감몰림 테스트 ', seq.n), '부하 테스트용 상품입니다.', 'ACTIVE', NOW(), NOW()
FROM seq JOIN leaf ON leaf.rn = 1 + seq.n % leaf.cnt;

INSERT INTO auctions (id, product_id, start_price, bid_unit, buy_now_price, current_price, bid_count, status,
                      start_at, end_at, version, extend_count, close_fail_count, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @count)
SELECT 9100000 + n, 9100000 + n, 1000, 1000, NULL, 1000, 0, 'ONGOING',
       NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL @minutes MINUTE, 0, 0, 0, NOW(), NOW()
FROM seq
WHERE EXISTS (SELECT 1 FROM products p WHERE p.id = 9100000 + seq.n);

SELECT COUNT(*) AS auctions, MIN(end_at) AS end_at FROM auctions WHERE id BETWEEN 9100001 AND 9199999;
