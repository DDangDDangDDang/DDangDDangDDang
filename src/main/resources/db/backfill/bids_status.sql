-- 입찰 상태(bids.status: ACTIVE·OUTBID·WON) 도입 전에 들어간 입찰의 상태를 채우는 backfill. MySQL 8 기준.
-- 상태가 비어 있는(null) 입찰만 바꾸므로 여러 번 실행해도 결과가 같다. (멱등)
--
-- 왜 필요한가: 새 컬럼은 기존 입찰에서 비어 있어(null), 입찰 기록 화면에 상태가 표시되지 않는다.
--              (입찰·마감·즉시구매 처리와 "내 입찰 상태"는 상태 컬럼이 아니라 경매를 기준으로 하므로 비어 있어도 동작한다)
--
-- 언제 실행하나: ddl-auto가 update인 DB에서, 새 버전 서버가 처음 뜬 직후 한 번 실행한다. (컬럼은 서버 시작 때 만들어진다)
--               그 사이 들어온 새 입찰은 상태가 채워져 있어 건드리지 않는다.
--
-- 규칙: 경매의 최고 입찰(최고 입찰자 + 현재가와 금액이 같은 가장 최근 입찰)은 진행 중이면 ACTIVE, 낙찰로 끝났으면 WON.
--       나머지는 모두 OUTBID. (즉시구매로 끝난 경매는 현재가가 즉시구매가라 맞는 입찰이 없어 전부 OUTBID가 된다)
UPDATE bids b
    LEFT JOIN (SELECT a.status   AS auction_status,
                      MAX(t.id)  AS bid_id
               FROM auctions a
                        JOIN bids t ON t.auction_id = a.id
                   AND t.bidder_id = a.highest_bidder_id
                   AND t.amount = a.current_price
               WHERE a.status IN ('ONGOING', 'ENDED')
               GROUP BY a.id, a.status) top ON b.id = top.bid_id
SET b.status = CASE
                   WHEN top.bid_id IS NULL THEN 'OUTBID'
                   WHEN top.auction_status = 'ONGOING' THEN 'ACTIVE'
                   ELSE 'WON'
    END
WHERE b.status IS NULL;
