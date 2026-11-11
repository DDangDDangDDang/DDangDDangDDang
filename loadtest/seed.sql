-- 부하 테스트용 회원과 포인트 지갑을 만든다. (로컬 DB 전용, 운영·dev에서 실행 금지)
-- 회원 번호 900000: 판매자(대량 경매용, seed-auctions.sql에서 씀), 900001~: 입찰자
-- 이메일 loadtest001@ddang.local ~, 비밀번호 Loadtest1!, 입찰자마다 사용 가능 포인트 1억
-- 입찰자 수는 아래 @bidders로 정한다 (최대 999). 이미 있는 회원·지갑은 건너뛰므로 여러 번 실행해도 된다.
-- ⚠ 백엔드를 최신 코드로 한 번 켠 뒤에 실행한다. (DB 칸이 서버를 켤 때 만들어진다)

SET @bidders = 999;
SET SESSION cte_max_recursion_depth = 1000;

-- 판매자 (대량 경매의 상품 주인)
INSERT IGNORE INTO users (id, email, password, nickname, role, phone_number, created_at, updated_at)
VALUES (900000, 'loadtest-seller@ddang.local', '$2a$10$UJr.Y55u1QDRN44StZ6obuu4aZ3pTY5mT5V0bRdfcMQ675epTMQWa',
        'lt-seller', 'USER', '01090009999', NOW(), NOW());

-- 입찰자
INSERT IGNORE INTO users (id, email, password, nickname, role, phone_number, created_at, updated_at)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @bidders)
SELECT 900000 + n,
       CONCAT('loadtest', LPAD(n, 3, '0'), '@ddang.local'),
       '$2a$10$UJr.Y55u1QDRN44StZ6obuu4aZ3pTY5mT5V0bRdfcMQ675epTMQWa',
       CONCAT('lt', LPAD(n, 3, '0')),
       'USER',
       CONCAT('0109000', LPAD(n, 4, '0')),
       NOW(), NOW()
FROM seq;

INSERT IGNORE INTO point_wallets (user_id, balance, held_balance, earned_balance, reclaim_debt, version, created_at, updated_at)
SELECT id, 100000000, 0, 0, 0, 0, NOW(), NOW()
FROM users WHERE id BETWEEN 900000 AND 900000 + @bidders;

-- 확인: 입찰자 수(@bidders)와 지갑 수가 같으면 성공
SELECT COUNT(*) AS bidders FROM users WHERE id BETWEEN 900001 AND 900000 + @bidders;
SELECT COUNT(*) AS wallets FROM point_wallets WHERE user_id BETWEEN 900001 AND 900000 + @bidders;
