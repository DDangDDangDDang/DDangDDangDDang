-- 종합 시나리오(인기 방송) 준비. ⚠ 판매자가 방송을 "시작하기 전에" 실행한다. (로컬 DB 전용, dev는 IntelliJ ddang-dev)
-- 1) 테스트 회원 @subscribers명이 방송 판매자를 구독 → 방송을 시작하는 순간 "방송 시작" 알림이 몰린다
-- 2) 그 방송에 올라간 경매 @auctions개, @minutes분 뒤 마감 → 방송 중에 입찰이 몰리고 한꺼번에 마감된다
-- 순서: 방송 만들기(시작 X) → 이 SQL → 스튜디오에서 방송 시작 → 바로 k6 showtime.js
-- @live: 방송 번호 (시청 화면 주소 /lives/번호), @minutes: k6의 MINUTES와 같은 값

SET @live = 1;
SET @subscribers = 500;
SET @auctions = 5;
SET @minutes = 6;
SET SESSION cte_max_recursion_depth = 2000;

SET @seller = (SELECT seller_id FROM live_streams WHERE id = @live);

-- 1. 구독 (이미 구독했으면 건너뜀)
INSERT IGNORE INTO seller_subscriptions (subscriber_id, seller_id, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @subscribers)
SELECT 900000 + n, @seller, NOW(), NOW() FROM seq
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = 900000 + seq.n);

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

-- 확인
SELECT @seller AS seller_id,
       (SELECT COUNT(*) FROM seller_subscriptions WHERE seller_id = @seller AND subscriber_id BETWEEN 900001 AND 900999) AS subscribers,
       (SELECT COUNT(*) FROM auctions WHERE id BETWEEN 9200001 AND 9299999) AS live_auctions,
       (SELECT MIN(end_at) FROM auctions WHERE id BETWEEN 9200001 AND 9299999) AS end_at;
