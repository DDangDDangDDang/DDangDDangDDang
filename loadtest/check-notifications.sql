-- 시나리오 D 결과: "상위 입찰자가 나왔어요" 알림이 밀렸는지 본다. realtime-bid.js가 끝나고 실행한다.
-- @auction을 테스트한 경매 번호로 바꾼다.
SET @auction = 9;

-- 1. 기대 알림 수 vs 실제: 입찰이 들어올 때마다 바로 앞 최고 입찰자(다른 사람)에게 알림 1건
SELECT
    (SELECT COUNT(*) FROM (
        SELECT bidder_id, LAG(bidder_id) OVER (ORDER BY id) AS prev_bidder
        FROM bids WHERE auction_id = @auction) t
     WHERE prev_bidder IS NOT NULL AND prev_bidder <> bidder_id) AS expected_outbid,
    (SELECT COUNT(*) FROM notifications WHERE auction_id = @auction AND type = 'AUCTION_OUTBID') AS actual_outbid;

-- 2. 알림이 얼마나 늦게 만들어졌나: 각 알림 직전에 들어온 입찰과의 시간 차(초)
SELECT COUNT(*) AS notifications,
       ROUND(AVG(delay) / 1000000, 3) AS avg_delay_sec,
       ROUND(MAX(delay) / 1000000, 3) AS max_delay_sec
FROM (
    SELECT TIMESTAMPDIFF(MICROSECOND,
               (SELECT MAX(b.created_at) FROM bids b WHERE b.auction_id = n.auction_id AND b.created_at <= n.created_at),
               n.created_at) AS delay
    FROM notifications n
    WHERE n.auction_id = @auction AND n.type = 'AUCTION_OUTBID') d;

-- 3. 입찰이 멈춘 뒤에도 알림이 계속 만들어졌나 (밀린 알림을 다 처리하는 데 걸린 시간, 초)
--    0~1초면 밀리지 않은 것. 수 초 이상이면 알림 스레드 대기열이 쌓였던 것
SELECT ROUND(TIMESTAMPDIFF(MICROSECOND,
           (SELECT MAX(created_at) FROM bids WHERE auction_id = @auction),
           (SELECT MAX(created_at) FROM notifications WHERE auction_id = @auction AND type = 'AUCTION_OUTBID')) / 1000000, 3) AS drain_after_last_bid_sec;
