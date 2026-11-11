-- 다음 입찰 테스트를 깨끗하게 시작하도록 부하 테스트 입찰자의 입찰·포인트를 처음 상태로 되돌린다. (로컬 DB 전용)
-- 실행한 뒤에는 새 경매를 만들어 그 경매로 테스트한다. (지난 경매에는 묶인 포인트 기록이 남아 있어서)

DELETE FROM notifications WHERE user_id BETWEEN 900000 AND 900999;
DELETE FROM bids WHERE bidder_id BETWEEN 900000 AND 900999;
DELETE FROM point_histories WHERE wallet_id IN (SELECT id FROM (SELECT id FROM point_wallets WHERE user_id BETWEEN 900000 AND 900999) w);
UPDATE point_wallets SET balance = 100000000, held_balance = 0, earned_balance = 0, reclaim_debt = 0
WHERE user_id BETWEEN 900000 AND 900999;

SELECT COUNT(*) AS bids_left FROM bids WHERE bidder_id BETWEEN 900000 AND 900999;
