-- 구경 테스트용 대량 경매. 판매자 900000(seed.sql)의 상품과 경매를 @auctions개 만든다. (로컬 DB 전용)
-- 상품·경매 번호는 9000001부터 (9000000 + n). 이미 있으면 건너뛴다.
-- 상태: 70% 진행 중, 15% 시작 전(1~48시간 뒤 시작), 15% 유찰(이미 끝남)
-- 진행 중·시작 전 경매의 마감은 30~33일 뒤로 둔다. 테스트 중에 마감 처리가 돌지 않게 하기 위해서다.
-- (입찰 수만 넣고 최고 입찰자는 없어서, 마감 처리가 돌면 낙찰 처리에 실패해 오류 로그가 쌓인다)
-- 진행 중 경매는 입찰 수(0~59)와 현재가가 섞여 있어 인기순·가격순 정렬이 의미 있다.
-- ⚠ 구경(조회)용이다. 이 경매들에는 입찰하지 않는다. (입찰 수만 있고 최고 입찰자·묶인 포인트가 없다)
-- ⚠ seed.sql을 먼저 실행하고, 카테고리(하위 분류)가 하나 이상 있어야 한다.

SET @auctions = 10000;  -- dev에서 팀원과 같은 조건이면 2000
SET SESSION cte_max_recursion_depth = 100000;

-- 상품: 하위 카테고리에 골고루 나눠 넣는다
INSERT IGNORE INTO products (id, seller_id, category_id, name, description, status, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @auctions),
     leaf AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn, COUNT(*) OVER () AS cnt
              FROM categories c
              WHERE c.parent_id IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM categories child WHERE child.parent_id = c.id))
SELECT 9000000 + seq.n,
       900000,
       leaf.id,
       CONCAT('부하테스트 ', ELT(1 + seq.n % 8, '카메라', '시계', '운동화', '가방', '노트북', '이어폰', '책', '피규어'), ' ', seq.n),
       '부하 테스트용 상품입니다.',
       'ACTIVE',
       NOW() - INTERVAL seq.n SECOND,
       NOW() - INTERVAL seq.n SECOND
FROM seq
JOIN leaf ON leaf.rn = 1 + seq.n % leaf.cnt;

-- 경매
INSERT IGNORE INTO auctions (id, product_id, start_price, bid_unit, buy_now_price, current_price, bid_count, status,
                             start_at, end_at, version, extend_count, close_fail_count, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @auctions),
     plan AS (SELECT n,
                     CASE WHEN n % 20 < 14 THEN 'ONGOING' WHEN n % 20 < 17 THEN 'READY' ELSE 'FAILED' END AS status,
                     1000 * (1 + n % 100) AS start_price,
                     CASE WHEN n % 20 < 14 THEN (n * 7) % 60 ELSE 0 END AS bid_count
              FROM seq)
SELECT 9000000 + n,
       9000000 + n,
       start_price,
       1000,
       NULL,
       start_price + bid_count * 1000,
       bid_count,
       status,
       CASE status
           WHEN 'ONGOING' THEN NOW() - INTERVAL (n % 48) HOUR
           WHEN 'READY' THEN NOW() + INTERVAL (1 + n % 48) HOUR
           ELSE NOW() - INTERVAL (72 + n % 100) HOUR END,
       CASE status
           WHEN 'ONGOING' THEN NOW() + INTERVAL 30 DAY + INTERVAL (n % 72) HOUR
           WHEN 'READY' THEN NOW() + INTERVAL 30 DAY + INTERVAL (25 + n % 48) HOUR
           ELSE NOW() - INTERVAL (48 + n % 100) HOUR END,
       0, 0, 0,
       NOW() - INTERVAL n SECOND,
       NOW() - INTERVAL n SECOND
FROM plan
WHERE EXISTS (SELECT 1 FROM products p WHERE p.id = 9000000 + plan.n);

-- 확인
SELECT status, COUNT(*) AS auctions FROM auctions WHERE id BETWEEN 9000001 AND 9000000 + @auctions GROUP BY status;
