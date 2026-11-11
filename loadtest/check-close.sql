-- 시나리오 E 결과: close-rush.js가 끝나고 1~2분 뒤에 실행한다.

-- 1. 경매 상태: 입찰이 있었으면 모두 ENDED(낙찰), 없었으면 FAILED(유찰)여야 한다. ONGOING이 남아 있으면 마감이 밀린 것
SELECT status, COUNT(*) AS auctions, SUM(bid_count) AS bids, MAX(close_fail_count) AS max_close_fail
FROM auctions WHERE id BETWEEN 9100001 AND 9199999 GROUP BY status;

-- 2. 낙찰된 경매마다 주문이 하나씩 생겼는지 (auctions_won = orders 여야 함)
SELECT (SELECT COUNT(*) FROM auctions WHERE id BETWEEN 9100001 AND 9199999 AND status = 'ENDED') AS auctions_won,
       (SELECT COUNT(*) FROM orders WHERE auction_id BETWEEN 9100001 AND 9199999) AS orders;

-- 3. 마감 처리 지연: 마감 시각(마지막 연장 반영)부터 주문이 생길 때까지 걸린 시간(초)
SELECT COUNT(*) AS orders,
       ROUND(AVG(TIMESTAMPDIFF(MICROSECOND, a.end_at, o.created_at)) / 1000000, 2) AS avg_delay_sec,
       ROUND(MAX(TIMESTAMPDIFF(MICROSECOND, a.end_at, o.created_at)) / 1000000, 2) AS max_delay_sec
FROM orders o JOIN auctions a ON a.id = o.auction_id
WHERE a.id BETWEEN 9100001 AND 9199999;

-- 4. 낙찰·판매·낙찰 실패 알림이 나갔는지 (낙찰 1건마다 AUCTION_WON 1, AUCTION_SOLD 1이 기대값)
SELECT type, COUNT(*) AS notifications
FROM notifications WHERE auction_id BETWEEN 9100001 AND 9199999
GROUP BY type ORDER BY type;

-- 5. 포인트 정합성: 낙찰자의 묶인 포인트가 남아 있으면 안 된다 (마감 후 테스트 회원 held_balance 합 = 진행 중 경매에 묶인 것만)
SELECT SUM(held_balance) AS held_total FROM point_wallets WHERE user_id BETWEEN 900001 AND 900999;
