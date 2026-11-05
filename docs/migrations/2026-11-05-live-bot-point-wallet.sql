-- 라이브 봇 계정에 포인트 지갑 채워 넣기
--
-- 봇 계정도 실제 경매·주문의 판매자인데, 지금까지는 봇을 만들 때 지갑을 만들지 않았다.
-- 그래서 봇이 판 주문의 구매 확정 정산·낙찰 취소 위약금이 WALLET_NOT_FOUND로 실패한다.
-- 이 버전부터는 봇을 만들 때 지갑도 만들고, 이 SQL은 이미 만든 봇 계정에 빈 지갑을 넣는다.
--
-- 대상은 live_bots가 아니라 봇 계정의 이메일 형식(User.createBot: bot-<uuid>@bot.invalid)으로 찾는다.
-- 봇을 지우면 live_bots 행은 사라지지만 계정은 남고, 발송 완료(SHIPPING) 주문이 남아 있어도 지울 수 있어서
-- 지운 봇의 계정에도 정산할 지갑이 필요하다. (.invalid 도메인은 가입 이메일 인증을 통과할 수 없어 실제 회원과 겹치지 않는다)
--
-- 실행 시점: 이 버전의 배포가 끝난 뒤(옛 버전 서버가 모두 내려간 뒤) 돌린다.
-- 배포 전에 돌리면 그 사이 옛 버전에서 만든 봇은 다시 지갑 없이 남는다. 배포 전에 미리 돌렸다면 배포 뒤 1)로 한 번 더 확인한다.
-- 지갑이 없는 봇 계정만 넣으므로 여러 번 돌려도 아무것도 바뀌지 않는다.

-- 1) 확인: 지갑이 없는 봇 계정 (bot_id가 비어 있으면 이미 지운 봇)
SELECT u.id AS user_id, u.nickname, b.id AS bot_id
FROM users u
LEFT JOIN live_bots b ON b.user_id = u.id
LEFT JOIN point_wallets w ON w.user_id = u.id
WHERE u.email LIKE 'bot-%@bot.invalid' AND w.id IS NULL;

-- 2) 채워 넣기 (PointWallet.create와 같은 초기값)
INSERT INTO point_wallets (user_id, balance, held_balance, earned_balance, reclaim_debt, version, created_at, updated_at)
SELECT u.id, 0, 0, 0, 0, 0, NOW(6), NOW(6)
FROM users u
LEFT JOIN point_wallets w ON w.user_id = u.id
WHERE u.email LIKE 'bot-%@bot.invalid' AND w.id IS NULL;
