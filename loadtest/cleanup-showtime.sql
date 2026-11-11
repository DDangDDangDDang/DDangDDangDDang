-- 종합 시나리오 데이터를 지운다. 끝나면 꼭 실행한다. (@live를 테스트한 방송 번호로)
-- ⚠ 주문을 오래 두면 배송지 미입력(72시간) 처리로 테스트 회원이 제재를 받을 수 있다.
SET @live = 1;
SET @seller = (SELECT seller_id FROM live_streams WHERE id = @live);
UPDATE live_streams SET featured_auction_id = NULL WHERE id = @live AND featured_auction_id BETWEEN 9200001 AND 9299999;
DELETE FROM notifications WHERE auction_id BETWEEN 9200001 AND 9299999;
DELETE FROM notifications WHERE live_stream_id = @live AND type = 'LIVE_STARTED' AND user_id BETWEEN 900001 AND 900999;
DELETE FROM auction_closing_notices WHERE auction_id BETWEEN 9200001 AND 9299999;
DELETE FROM auction_alerts WHERE auction_id BETWEEN 9200001 AND 9299999;
DELETE FROM orders WHERE auction_id BETWEEN 9200001 AND 9299999;
DELETE FROM bids WHERE auction_id BETWEEN 9200001 AND 9299999;
DELETE FROM auctions WHERE id BETWEEN 9200001 AND 9299999;
DELETE FROM products WHERE id BETWEEN 9200001 AND 9299999;
DELETE FROM seller_subscriptions WHERE seller_id = @seller AND subscriber_id BETWEEN 900001 AND 900999;
SELECT COUNT(*) AS live_auctions_left FROM auctions WHERE id BETWEEN 9200001 AND 9299999;
