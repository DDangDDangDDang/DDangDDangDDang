-- 종합 시나리오 결과. k6가 끝나고 1~2분 뒤에 실행한다. @live를 테스트한 방송 번호로 바꾼다.
SET @live = 1;

-- 1. 방송 시작 알림: 구독자 수만큼 나갔는지, 다 만드는 데 얼마나 걸렸는지(초)
SELECT COUNT(*) AS live_started_notifications,
       ROUND(TIMESTAMPDIFF(MICROSECOND, MIN(created_at), MAX(created_at)) / 1000000, 2) AS fanout_sec
FROM notifications WHERE live_stream_id = @live AND type = 'LIVE_STARTED' AND user_id BETWEEN 900001 AND 900999;

-- 2. 방송 경매 마감: 입찰 있으면 ENDED, 없으면 FAILED. ONGOING이 남으면 마감이 밀린 것
SELECT status, COUNT(*) AS auctions, SUM(bid_count) AS bids, MAX(close_fail_count) AS max_close_fail
FROM auctions WHERE id BETWEEN 9200001 AND 9299999 GROUP BY status;

-- 3. 낙찰 수 = 주문 수, 마감→주문 지연(초)
SELECT (SELECT COUNT(*) FROM auctions WHERE id BETWEEN 9200001 AND 9299999 AND status = 'ENDED') AS auctions_won,
       COUNT(o.id) AS orders,
       ROUND(AVG(TIMESTAMPDIFF(MICROSECOND, a.end_at, o.created_at)) / 1000000, 2) AS avg_close_delay_sec,
       ROUND(MAX(TIMESTAMPDIFF(MICROSECOND, a.end_at, o.created_at)) / 1000000, 2) AS max_close_delay_sec
FROM auctions a LEFT JOIN orders o ON o.auction_id = a.id
WHERE a.id BETWEEN 9200001 AND 9299999 AND a.status = 'ENDED';

-- 4. "상위 입찰자 나옴" 알림: 기대 수 vs 실제, 입찰이 멈춘 뒤 밀린 알림 처리 시간(초)
SELECT
    (SELECT COUNT(*) FROM (
        SELECT auction_id, bidder_id, LAG(bidder_id) OVER (PARTITION BY auction_id ORDER BY id) AS prev_bidder
        FROM bids WHERE auction_id BETWEEN 9200001 AND 9299999) t
     WHERE prev_bidder IS NOT NULL AND prev_bidder <> bidder_id) AS expected_outbid,
    (SELECT COUNT(*) FROM notifications WHERE auction_id BETWEEN 9200001 AND 9299999 AND type = 'AUCTION_OUTBID') AS actual_outbid,
    ROUND(TIMESTAMPDIFF(MICROSECOND,
        (SELECT MAX(created_at) FROM bids WHERE auction_id BETWEEN 9200001 AND 9299999),
        (SELECT MAX(created_at) FROM notifications WHERE auction_id BETWEEN 9200001 AND 9299999 AND type = 'AUCTION_OUTBID')) / 1000000, 2) AS outbid_drain_sec;

-- 5. 낙찰·판매 알림
SELECT type, COUNT(*) AS notifications FROM notifications
WHERE auction_id BETWEEN 9200001 AND 9299999 AND type <> 'AUCTION_OUTBID' GROUP BY type;
