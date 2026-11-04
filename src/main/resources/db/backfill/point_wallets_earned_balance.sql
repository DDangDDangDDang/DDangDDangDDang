-- 포인트 지갑의 "번 포인트"(point_wallets.earned_balance) 도입 전 지갑의 값을 채우는 backfill. MySQL 8 기준.
--
-- 왜 필요한가: 충전한 포인트는 환전할 수 없게 바꾸면서(fix/point-exchange-and-charge-safety), 판매 정산·위약금으로 번 포인트만
--              환전할 수 있도록 earned_balance 컬럼을 새로 만들었다. 새 컬럼은 기존 지갑에서 0이라, 그대로 두면
--              이미 정산받은 판매자도 환전할 수 없다.
--
-- 언제 실행하나: ddl-auto가 update인 DB에서, 새 버전 서버가 처음 뜬 직후 "한 번만" 실행한다. (컬럼은 서버 시작 때 만들어진다)
--               다시 실행하면 그 사이 쌓인 "충전한 포인트부터 쓴다" 계산을 아래 근사값으로 덮어쓰므로 여러 번 실행하지 않는다.
--
-- 규칙(근사): 번 포인트 = 정산 + 위약금 수령 + 환전 환불 - 환전 신청. 0보다 작으면 0, 지갑 전체(사용 가능 + 홀딩)보다 크면 지갑 전체.
--            낙찰·즉시구매에 쓴 포인트는 충전한 포인트부터 쓴 것으로 보고 빼지 않는다. (지갑 전체로 자르는 것으로 맞춘다)
UPDATE point_wallets w
    LEFT JOIN (SELECT h.wallet_id,
                      SUM(CASE h.type
                              WHEN 'SETTLE' THEN h.amount
                              WHEN 'ORDER_PENALTY' THEN h.amount
                              WHEN 'EXCHANGE_REFUND' THEN h.amount
                              WHEN 'EXCHANGE' THEN -h.amount
                              ELSE 0
                          END) AS earned
               FROM point_histories h
               GROUP BY h.wallet_id) e ON e.wallet_id = w.id
SET w.earned_balance = LEAST(w.balance + w.held_balance, GREATEST(0, COALESCE(e.earned, 0)));
