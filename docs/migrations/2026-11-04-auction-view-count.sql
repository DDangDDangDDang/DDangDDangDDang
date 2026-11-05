-- 경매 조회수(인기 경매 점수에 쓴다). 스케줄러가 Redis 조회수를 옮겨 적는다
ALTER TABLE auctions ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;