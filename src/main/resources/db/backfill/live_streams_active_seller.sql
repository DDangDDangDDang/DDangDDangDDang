-- 판매자당 종료되지 않은 방송 하나 규칙(live_streams.active_seller_id + uk_live_stream_active_seller) 도입 전에
-- 만들어진 방송을 규칙에 맞추는 backfill. MySQL 8 기준. 여러 번 실행해도 결과가 같다. (멱등)
--
-- 왜 필요한가: 새 컬럼은 기존 행에서 비어 있어(null), 이미 있던 대기 중·방송 중 방송이 규칙에서 빠진다.
--              (그 판매자는 종료되지 않은 방송이 있는데도 새 방송을 만들 수 있음)
--
-- 언제 실행하나: ddl-auto가 update인 DB에서, 새 버전 서버가 처음 뜬 직후 (컬럼과 유니크 제약은 서버 시작 때 만들어진다.
--               이때 기존 행은 모두 null이라 제약 생성은 성공한다) 다른 요청이 들어오기 전에 한 번 실행한다.
--               컬럼을 직접 추가하는 환경이라면: 컬럼 추가 → 이 스크립트 → 유니크 제약 추가 순서로 실행한다.
--               ddl-auto가 create인 로컬 DB는 서버를 다시 띄우면 데이터가 지워지므로 실행할 필요가 없다.

-- 1. 판매자마다 종료되지 않은 방송이 여러 개면 하나만 남기고 종료 처리한다.
--    남길 방송: 방송 중(LIVE)인 것, 없으면 가장 최근에 만든 것.
--    종료 처리한 방송에 LiveKit 방이 남아 있다면 LiveKit에서 따로 지워야 한다. (서버의 방송 종료 API를 거치지 않으므로)
UPDATE live_streams ls
    JOIN (SELECT seller_id,
                 COALESCE(MAX(CASE WHEN status = 'LIVE' THEN id END), MAX(id)) AS keep_id
          FROM live_streams
          WHERE status IN ('SCHEDULED', 'LIVE')
          GROUP BY seller_id
          HAVING COUNT(*) > 1) duplicated ON ls.seller_id = duplicated.seller_id
SET ls.status           = 'ENDED',
    ls.ended_at         = NOW(6),
    ls.active_seller_id = NULL,
    ls.version          = COALESCE(ls.version, 0) + 1
WHERE ls.status IN ('SCHEDULED', 'LIVE')
  AND ls.id <> duplicated.keep_id;

-- 2. 남은 종료되지 않은 방송에 active_seller_id를 채운다. 이미 채워진 행은 건드리지 않는다.
UPDATE live_streams
SET active_seller_id = seller_id
WHERE status IN ('SCHEDULED', 'LIVE')
  AND active_seller_id IS NULL;
