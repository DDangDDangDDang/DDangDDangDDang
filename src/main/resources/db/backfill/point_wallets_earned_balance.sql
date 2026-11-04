-- 포인트 지갑의 "번 포인트"(point_wallets.earned_balance)와 구매 내역의 "번 포인트로 낸 몫"(point_histories.earned_portion)을
-- 도입 전 데이터에 채우는 backfill. MySQL 8 기준.
--
-- 왜 필요한가: 충전한 포인트는 환전할 수 없게 바꾸면서(fix/point-exchange-and-charge-safety), 판매 정산·위약금으로 번 포인트만
--              환전할 수 있도록 earned_balance 컬럼을 새로 만들었다. 새 컬럼은 기존 지갑에서 0이라, 그대로 두면
--              이미 정산받은 판매자도 환전할 수 없다.
--
-- 언제 실행하나: ddl-auto가 update인 DB에서, 새 버전 서버가 처음 뜬 직후 "한 번만" 실행한다. (컬럼은 서버 시작 때 만들어진다)
--
-- 계산 방법: 지갑마다 point_histories를 id 순으로 다시 돌며 서버와 같은 규칙을 적용한다. (PointWallet 참고)
--   - 번 포인트가 늘어남: SETTLE(정산), ORDER_PENALTY(위약금 수령), EXCHANGE_REFUND(환전 환불)
--   - 번 포인트에서만 빠짐: EXCHANGE(환전 신청)
--   - 충전한 포인트부터 쓰고 모자란 만큼만 번 포인트에서 빠짐: PURCHASE, WIN_CANCEL_PENALTY, CHARGE_CANCEL
--     (그 시점 지갑 전체 = 직전 내역의 balance_after + held_balance_after, 충전한 포인트 = 지갑 전체 - 번 포인트)
--   - 그 밖(CHARGE, HOLD, RELEASE, ORDER_REFUND, WIN_CANCEL)은 번 포인트가 그대로다.
--     주문 취소 환불(ORDER_REFUND, WIN_CANCEL)은 예전 데이터라 충전한 포인트로 돌아온 것으로 본다. (환전할 수 없는 쪽으로 안전하게)
--   마지막 값을 지갑 전체(사용 가능 + 홀딩) 안으로 자른다.
-- 그래서 "정산 → 그 돈으로 구매 → 충전" 순서인 지갑은 번 포인트가 0이 되어, 나중에 충전한 포인트를 환전할 수 없다.
--
-- 다시 실행해도 같은 내역으로 같은 값을 다시 계산하므로 결과가 같다. (배포 뒤 새로 쌓인 내역도 같은 규칙으로 계산된다)

SET SESSION cte_max_recursion_depth = 10000000;

DROP TABLE IF EXISTS tmp_point_earned_replay;
DROP TABLE IF EXISTS tmp_point_earned_result;

-- 지갑별 순번(rn)과 직전 지갑 전체(total_before). 재귀 CTE가 두 번 참조하므로 TEMPORARY가 아닌 보통 테이블로 만든다.
CREATE TABLE tmp_point_earned_replay
(
    wallet_id    BIGINT      NOT NULL,
    rn           BIGINT      NOT NULL,
    id           BIGINT      NOT NULL,
    type         VARCHAR(20) NOT NULL,
    amount       BIGINT      NOT NULL,
    total_before BIGINT      NOT NULL,
    PRIMARY KEY (wallet_id, rn)
);

INSERT INTO tmp_point_earned_replay (wallet_id, rn, id, type, amount, total_before)
SELECT h.wallet_id,
       ROW_NUMBER() OVER (PARTITION BY h.wallet_id ORDER BY h.id),
       h.id,
       h.type,
       h.amount,
       COALESCE(LAG(h.balance_after + h.held_balance_after) OVER (PARTITION BY h.wallet_id ORDER BY h.id), 0)
FROM point_histories h;

CREATE TABLE tmp_point_earned_result
(
    wallet_id      BIGINT NOT NULL,
    rn             BIGINT NOT NULL,
    id             BIGINT NOT NULL,
    earned         BIGINT NOT NULL,
    earned_portion BIGINT NOT NULL,
    PRIMARY KEY (wallet_id, rn)
);

-- earned: 이 내역 뒤 번 포인트, earned_portion: 이 내역에서 번 포인트로 낸 몫
-- 쓰는 내역의 번 포인트 몫 = LEAST(번 포인트, GREATEST(0, 금액 - GREATEST(0, 지갑 전체 - 번 포인트)))
INSERT INTO tmp_point_earned_result (wallet_id, rn, id, earned, earned_portion)
WITH RECURSIVE replay (wallet_id, rn, id, earned, earned_portion) AS (
    SELECT t.wallet_id, t.rn, t.id,
           CAST(CASE
                    WHEN t.type IN ('SETTLE', 'ORDER_PENALTY', 'EXCHANGE_REFUND') THEN t.amount
                    ELSE 0
               END AS SIGNED),
           CAST(0 AS SIGNED)
    FROM tmp_point_earned_replay t
    WHERE t.rn = 1
    UNION ALL
    SELECT t.wallet_id, t.rn, t.id,
           CASE
               WHEN t.type IN ('SETTLE', 'ORDER_PENALTY', 'EXCHANGE_REFUND') THEN r.earned + t.amount
               WHEN t.type = 'EXCHANGE' THEN GREATEST(0, r.earned - t.amount)
               WHEN t.type IN ('PURCHASE', 'WIN_CANCEL_PENALTY', 'CHARGE_CANCEL')
                   THEN r.earned - LEAST(r.earned, GREATEST(0, t.amount - GREATEST(0, t.total_before - r.earned)))
               ELSE r.earned
           END,
           CASE
               WHEN t.type IN ('PURCHASE', 'WIN_CANCEL_PENALTY', 'CHARGE_CANCEL')
                   THEN LEAST(r.earned, GREATEST(0, t.amount - GREATEST(0, t.total_before - r.earned)))
               ELSE 0
           END
    FROM replay r
             JOIN tmp_point_earned_replay t ON t.wallet_id = r.wallet_id AND t.rn = r.rn + 1
)
SELECT wallet_id, rn, id, earned, earned_portion
FROM replay;

-- 예전 구매 내역에 번 포인트로 낸 몫을 남긴다. (그 주문이 취소되면 그만큼 환전할 수 있는 포인트로 돌려준다)
UPDATE point_histories h
    JOIN tmp_point_earned_result r ON r.id = h.id
SET h.earned_portion = r.earned_portion
WHERE h.type = 'PURCHASE';

-- 지갑의 번 포인트 = 마지막 내역 뒤 값. 지갑 전체(사용 가능 + 홀딩) 안으로 자른다.
UPDATE point_wallets w
    LEFT JOIN (SELECT r.wallet_id, r.earned
               FROM tmp_point_earned_result r
                        JOIN (SELECT wallet_id, MAX(rn) AS rn FROM tmp_point_earned_result GROUP BY wallet_id) last
                             ON last.wallet_id = r.wallet_id AND last.rn = r.rn) e ON e.wallet_id = w.id
SET w.earned_balance = LEAST(w.balance + w.held_balance, GREATEST(0, COALESCE(e.earned, 0)));

DROP TABLE tmp_point_earned_replay;
DROP TABLE tmp_point_earned_result;
