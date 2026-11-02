-- 즉시구매 전용 경매(시작가·최소 입찰 단위 없이 즉시구매가만 있는 경매)를 만들 수 있게
-- auctions.start_price, auctions.bid_unit의 NOT NULL을 푼다. MySQL 8 기준.
--
-- 왜 필요한가: ddl-auto: update는 새 컬럼은 만들어 주지만, 이미 있는 컬럼의 NOT NULL은 풀어 주지 않는다.
--              실행하지 않으면 즉시구매 전용 경매를 만들 때 "Column 'start_price' cannot be null" 오류가 난다.
--
-- 언제 실행하나: 새 버전을 배포하기 전이나 직후, 로컬·운영 DB에 각각 한 번. 여러 번 실행해도 결과가 같다. (멱등)
--               기존 경매의 값은 바뀌지 않는다.
ALTER TABLE auctions
    MODIFY COLUMN start_price BIGINT NULL,
    MODIFY COLUMN bid_unit BIGINT NULL;
