-- 라이브 봇 계정에 포인트 지갑 채워 넣기
--
-- 봇 계정도 실제 경매·주문의 판매자인데, 지금까지는 봇을 만들 때 지갑을 만들지 않았다.
-- 그래서 봇이 판 주문의 구매 확정 정산·낙찰 취소 위약금이 WALLET_NOT_FOUND로 실패한다.
-- 이 버전부터는 봇을 만들 때 지갑도 만들고, 이 SQL은 이미 만든 봇 계정에 빈 지갑을 넣는다.
--
-- 배포 전·후 언제 돌려도 된다. (지갑이 없는 봇 계정만 넣으므로 다시 돌려도 아무것도 바뀌지 않는다)

-- 1) 확인: 지갑이 없는 봇 계정
SELECT b.id AS bot_id, b.user_id
FROM live_bots b
LEFT JOIN point_wallets w ON w.user_id = b.user_id
WHERE w.id IS NULL;

-- 2) 채워 넣기 (PointWallet.create와 같은 초기값)
INSERT INTO point_wallets (user_id, balance, held_balance, earned_balance, reclaim_debt, version, created_at, updated_at)
SELECT b.user_id, 0, 0, 0, 0, 0, NOW(6), NOW(6)
FROM live_bots b
LEFT JOIN point_wallets w ON w.user_id = b.user_id
WHERE w.id IS NULL;
