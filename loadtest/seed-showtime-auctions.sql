-- 종합 시나리오 경매 준비: 방송에 올라간 경매 @auctions개, @minutes분 뒤 마감.
-- ⚠ 방송을 시작한 "뒤", k6를 시작하기 "바로 직전"에 실행한다. 경매 마감 시각이 이 SQL을 실행한 순간부터 계산돼서,
--   k6를 늦게 시작하면 입찰자가 들어올 때 경매가 이미 끝나 있다. Git Bash에서 k6와 한 줄로 이어서 실행하면 안전하다:
--   docker exec -i ddang-mysql mysql -u ddang -pddang ddang < seed-showtime-auctions.sql && k6 run ... showtime.js
SET @live = 1;
SET @auctions = 5;
SET @minutes = 6;
SET SESSION cte_max_recursion_depth = 2000;
SET @seller = (SELECT seller_id FROM live_streams WHERE id = @live);

-- 2. 방송 경매: 상품은 방송 판매자 것, 번호 9200001부터
INSERT INTO products (id, seller_id, category_id, name, description, status, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @auctions),
     leaf AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn, COUNT(*) OVER () AS cnt
              FROM categories c
              WHERE c.parent_id IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM categories child WHERE child.parent_id = c.id))
SELECT 9200000 + seq.n, @seller, leaf.id, CONCAT('방송 종합테스트 ', seq.n), '부하 테스트용 상품입니다.', 'ACTIVE', NOW(), NOW()
FROM seq JOIN leaf ON leaf.rn = 1 + seq.n % leaf.cnt;

INSERT INTO auctions (id, product_id, live_stream_id, start_price, bid_unit, buy_now_price, current_price, bid_count, status,
                      start_at, end_at, version, extend_count, close_fail_count, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @auctions)
SELECT 9200000 + n, 9200000 + n, @live, 1000, 1000, NULL, 1000, 0, 'ONGOING',
       NOW() - INTERVAL 1 MINUTE, NOW() + INTERVAL @minutes MINUTE, 0, 0, 0, NOW(), NOW()
FROM seq
WHERE EXISTS (SELECT 1 FROM products p WHERE p.id = 9200000 + seq.n);

-- 영상 위에 첫 번째 방송 경매를 띄운다
UPDATE live_streams SET featured_auction_id = 9200001 WHERE id = @live;

SELECT COUNT(*) AS live_auctions, MIN(end_at) AS end_at FROM auctions WHERE id BETWEEN 9200001 AND 9299999;
