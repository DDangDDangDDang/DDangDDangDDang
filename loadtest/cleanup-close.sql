-- 시나리오 E(마감 몰림) 데이터를 지운다. 다음 단계 전에, 그리고 끝나면 꼭 실행한다.
-- ⚠ 주문을 오래 두면 배송지 미입력(72시간) 처리로 테스트 회원이 제재를 받을 수 있다.
DELETE FROM notifications WHERE auction_id BETWEEN 9100001 AND 9199999;
DELETE FROM auction_closing_notices WHERE auction_id BETWEEN 9100001 AND 9199999;
DELETE FROM auction_alerts WHERE auction_id BETWEEN 9100001 AND 9199999;
DELETE FROM orders WHERE auction_id BETWEEN 9100001 AND 9199999;
DELETE FROM bids WHERE auction_id BETWEEN 9100001 AND 9199999;
DELETE FROM auctions WHERE id BETWEEN 9100001 AND 9199999;
DELETE FROM products WHERE id BETWEEN 9100001 AND 9199999;
SELECT COUNT(*) AS auctions_left FROM auctions WHERE id BETWEEN 9100001 AND 9199999;
