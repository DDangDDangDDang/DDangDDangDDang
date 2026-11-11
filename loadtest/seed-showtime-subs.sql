-- 종합 시나리오 1단계 준비: 테스트 회원이 방송 판매자를 구독한다. ⚠ 방송을 "시작하기 전에" 실행 (시작하는 순간 알림이 몰리게)
-- @live: 방송 번호 (시청 화면 주소 /lives/번호)
SET @live = 1;
SET @subscribers = 500;
SET SESSION cte_max_recursion_depth = 2000;
SET @seller = (SELECT seller_id FROM live_streams WHERE id = @live);

INSERT IGNORE INTO seller_subscriptions (subscriber_id, seller_id, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @subscribers)
SELECT 900000 + n, @seller, NOW(), NOW() FROM seq
WHERE EXISTS (SELECT 1 FROM users u WHERE u.id = 900000 + seq.n);

SELECT @seller AS seller_id,
       (SELECT COUNT(*) FROM seller_subscriptions WHERE seller_id = @seller AND subscriber_id BETWEEN 900001 AND 900999) AS subscribers;
