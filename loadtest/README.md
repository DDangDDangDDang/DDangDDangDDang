# 땅땅땅 부하 테스트 (로컬)

| 파일 | 내용 |
| --- | --- |
| `seed.sql` | 입찰자 500명 + 판매자 1명 + 포인트 지갑(각 1억 P). 처음 한 번만 |
| `seed-auctions.sql` | 구경용 대량 경매 1만 개 (진행 중 70%, 시작 전 15%, 유찰 15%) |
| `cleanup-auctions.sql` | 대량 경매 지우기 |
| `reset.sql` | 다음 테스트 전에 입찰·포인트를 처음 상태로 |
| `bid-race.js` | 시나리오 A. VUS명(기본 100)이 같은 경매에 입찰 |
| `browse.js` | 시나리오 B. VUS명이 목록(정렬 5종·페이지·검색)·상세·입찰 기록·광고 조회 |
| `mixed.js` | 시나리오 C. 입찰자 BIDDERS명(기본 20) + 구경꾼 VIEWERS명(기본 80) |
| `common.js` | 공통 (토큰 만들기, 입찰·구경 한 번의 동작) |

각 시나리오는 **30초 동안 목표 인원까지 늘리고 → 2분 유지 → 30초 줄이기**, 약 3분 걸려요.

---

## 0. 준비 (처음 한 번)

**k6 설치** (PowerShell 또는 Git Bash)

```bash
winget install k6 --source winget
```

설치 후 Git Bash를 새로 열고 `k6 version`이 나오면 끝이에요.

**이 폴더를 프로젝트 안에 두기**: `DDangDDangDDang/loadtest/` 처럼 두면 편해요. 결과 파일이 쌓이니 `.gitignore`에 `loadtest/results/`를 넣어 두세요.

## 1. 서버 켜기

1. Docker Desktop 켜기 → 프로젝트 루트에서 `docker compose up -d`
2. IntelliJ에서 백엔드 실행
   - 환경변수 `LOGGING_LEVEL_ORG_HIBERNATE_SQL=INFO` (SQL 로그가 켜져 있으면 로그 쓰느라 느려져서 숫자가 틀어져요)
   - `JWT_SECRET`은 넣지 마세요. 넣었다면 k6 실행 때 `-e JWT_SECRET=같은값`을 붙여야 해요

## 2. 테스트 회원 넣기 (처음 한 번)

Git Bash에서 `loadtest` 폴더로 가서:

```bash
docker exec -i ddang-mysql mysql -u ddang -pddang ddang < seed.sql
```

마지막에 `bidders 500`, `wallets 500`이 나오면 성공이에요. 입찰자 수는 파일 맨 위 `SET @bidders = 500;`에서 바꿔요 (최대 999).

**대량 경매 넣기** (구경 테스트용, 1~2분 걸려요)

```bash
docker exec -i ddang-mysql mysql -u ddang -pddang ddang < seed-auctions.sql
```

마지막에 상태별 개수(ONGOING 7000, READY 1500, FAILED 1500)가 나오면 성공이에요. 하위 카테고리가 하나도 없으면 0개가 들어가니, 관리자 화면에서 카테고리를 먼저 만들어 주세요. **이 경매들은 구경용이라 입찰하지 마세요.** (입찰 수만 있고 묶인 포인트가 없어요)

## 3. 테스트용 경매 만들기 (테스트마다 새로)

사이트에서 **판매자 계정**으로 경매를 하나 만들어요.

- 시작가 **1,000** / 최소 입찰 단위 **1,000** / 즉시구매가 **비움**
- 시작 **지금**, 마감 **3시간 뒤** (테스트 중에 끝나지 않게)

만든 경매 주소 `/auctions/123`의 **숫자(경매 번호)**를 적어 두세요.

## 4. 실행

```bash
mkdir -p results
# A. 입찰 경쟁: 100 → 300 → 500명으로 늘려 가며 (매번 reset.sql + 새 경매)
k6 run -e AUCTION_ID=123 -e VUS=300 --summary-export=results/bid-race-300.json bid-race.js
# B. 구경: 대량 경매 1만 개를 무작위로
k6 run -e BULK_AUCTIONS=10000 -e VUS=300 --summary-export=results/browse-300.json browse.js
# C. 섞어서: 입찰 100명 + 구경 400명
k6 run -e AUCTION_ID=123 -e BULK_AUCTIONS=10000 -e BIDDERS=100 -e VIEWERS=400 --summary-export=results/mixed-500.json mixed.js
```

| 옵션 | 의미 |
| --- | --- |
| `VUS` | bid-race·browse의 인원 (기본 100). bid-race는 seed.sql의 입찰자 수 이하로 |
| `BIDDERS`, `VIEWERS` | mixed의 입찰자·구경꾼 수 |
| `BULK_AUCTIONS` | 대량 경매 개수. 넣으면 구경꾼이 그중 아무 경매나 봐요 |

`123`은 3번에서 적은 경매 번호로 바꿔요. 시나리오 사이에 1분쯤 쉬면 서버가 정리할 시간이 생겨요.

## 5. 결과 읽기

실행이 끝나면 맨 아래에 요약이 나와요. 이 줄들을 보면 돼요.

| 줄 | 의미 | 좋은 상태 |
| --- | --- | --- |
| `http_req_duration{name:bid}` | 입찰 응답 시간. **p(95)**를 봐요 (95%가 이 안에 끝남) | 1초 안 |
| `http_req_duration{name:auction_list_LATEST}` 등 | 정렬별 목록 응답 시간 (`_POPULAR`, `_PRICE_ASC`…), 검색은 `auction_search` | 0.5초 안 |
| `http_req_failed` | 실패율 (입찰 거절은 정상이라 빠져 있어요) | 1% 미만 |
| `bid_success` | 입찰 성공 수 | |
| `bid_too_low` | 보는 사이 현재가가 올라 거절 (정상) | |
| `bid_already_top` | 이미 최고 입찰자라 거절 (정상) | |
| `bid_conflict` | 동시 입찰이 몰려 5번 재시도 후 실패 (`AUCTION_018`) | 적을수록 좋음 |
| `bid_unexpected` | 그 밖의 오류 | **0** (있으면 콘솔 경고 확인) |
| `http_reqs` | 초당 처리한 요청 수 | |

`✓`/`✗` 표시는 미리 정해 둔 기준(입찰 p95 1초 등)을 통과했는지예요.

## 6. 입찰 1건당 DB 쿼리 수 세기 (선택)

1. 백엔드 환경변수를 `LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG`로 바꾸고 재시작
2. 사이트에서 입찰 **한 번**
3. IntelliJ 콘솔에서 그 입찰 동안 찍힌 SQL 줄 수를 세요
4. 다 세면 다시 `INFO`로

개선 전후 비교에 가장 분명하게 보이는 숫자예요.

## 7. 다음 테스트 전 초기화

```bash
docker exec -i ddang-mysql mysql -u ddang -pddang ddang < reset.sql
```

그다음 **3번(새 경매 만들기)**부터 다시 해요. 지난 경매에는 테스트 회원의 묶인 포인트 기록이 남아 있어서 새 경매로 해야 숫자가 깨끗해요.

## 8. 개선 전후 비교

1. 개선 **전** 커밋으로 이동해서 서버 재시작 → 2~5번 → 결과를 `results/before-*.json`으로 저장
2. 개선 **후** 커밋으로 돌아와서 서버 재시작 → 7번 초기화 → 같은 방법으로 `results/after-*.json`
3. 아래 표 채우기

| 항목 | 개선 전 | 개선 후 |
| --- | --- | --- |
| 입찰 p95 (bid-race) | | |
| 조회 p95 (browse, auction_list) | | |
| 입찰 성공 / 충돌 수 (bid-race) | | |
| 초당 요청 수 (mixed) | | |
| 입찰 1건당 쿼리 수 | | |

```bash
git stash            # 지금 작업 잠깐 치우기 (커밋했다면 생략)
git checkout 개선전커밋해시
# ... 측정 ...
git checkout feat/point-external
git stash pop
```

## 주의

- **로컬 DB에서만** 하세요. `seed.sql`·`reset.sql`을 dev·운영 DB에 실행하면 안 돼요
- 테스트 회원은 로그인 API 대신 **k6가 직접 토큰을 만들어** 써요. 로그인 API가 IP당 5분에 30번으로 막혀 있어서, 100명이 한 번에 로그인하면 막히기 때문이에요
- 테스트 경매는 3시간 뒤 테스트 회원에게 낙찰돼 주문이 생겨요. 신경 쓰이면 마감을 짧게 잡지 말고 그냥 두거나, 끝난 뒤 로컬 DB를 초기화하세요

## dev 서버에서 (구경 테스트)

구경 테스트(`browse.js`)는 로그인이 필요 없어서 dev JWT 비밀값 없이 할 수 있어요. 상세 화면은 **방금 본 목록에서 경매를 골라** 보니 dev의 경매 번호를 몰라도 돼요.

```bash
k6 run -e BASE_URL=https://dev서버API주소 -e VUS=300 -e HOLD=3m30s \
  --summary-export=results/dev-browse-300.json browse.js 2>&1 | tee results/dev-browse-300.log
```

- `HOLD=3m30s`: 30초 늘리기 + 3분 30초 유지 + 30초 줄이기 = **4분 30초** (팀원 테스트와 같은 길이)
- `BULK_AUCTIONS`는 넣지 않아요 (그건 로컬 대량 경매 번호용)
- 기준: 목록·인기·검색 p95 500ms, 상세 p95 300ms (팀원 기준과 같음)
- **dev에서 시작하기 전에 팀에 알리고**, 사람 수를 줄여 `-e VUS=10 -e HOLD=30s`로 먼저 짧게 확인하세요

---

# 단계별 테스트 계획 (목표 숫자 + 3단계)

목표 숫자는 지금까지 결과를 보고 잡았어요. dev는 우리 조회 시나리오에서 300명일 때 이미 한계(초당 약 70요청)를 넘었으니, 그 아래부터 3단계로 올리며 **어디서 목표를 못 지키기 시작하는지** 찾아요. 단계마다 결과 파일 이름을 바꿔 저장하세요.

| 시나리오 | 목표 (통과 기준) | 1단계 | 2단계 | 3단계 |
| --- | --- | --- | --- | --- |
| B. 조회 `browse.js` | 목록·인기·검색 p95 ≤ 500ms, 상세 p95 ≤ 300ms, 실패 < 1% | 50명 | 100명 | 150명 |
| A. 입찰 `bid-race.js` | 입찰 p95 ≤ 1초, `bid_conflict` < 입찰 성공의 1%, `bid_unexpected` 0 | 50명 | 100명 | 200명 |
| D. 입찰+실시간 `realtime-bid.js` | 위 입찰 기준 + WebSocket 연결 ≥ 99%, 연결 p95 ≤ 1초, 알림 최대 지연 ≤ 3초, 밀린 알림 처리 ≤ 5초 | 입찰 20 + 구경 100 | 40 + 200 | 60 + 300 |
| E. 마감 몰림 `close-rush.js` | 마감 1분 안에 전부 ENDED/FAILED, 주문 수 = 낙찰 수, 마감→주문 최대 10초, `max_close_fail` 0 | 경매 10개, 30명 | 30개, 60명 | 60개, 100명 |
| F. 로그인 제한 `login-limit.js` | 30번까지 성공, 31번째부터 막힘 | 한 번 | | |
| 충전 웹훅 동시성 | 동시에 20번 와도 한 번만 지급 | 백엔드 테스트 (`./gradlew test`) | | |

## 준비 (dev)

- **테스트 회원**: dev DB에 `seed.sql` 전체 실행 (입찰자 500명 + 판매자)
- **dev JWT 비밀값**: 입찰 테스트(A·D·E)는 토큰을 직접 만들어서, dev 서버의 `JWT_SECRET`이 필요해요. 인프라 담당께 받고, **파일에 저장하지 말고** 실행할 때만 `-e JWT_SECRET='값'`으로 넘기세요
- **테스트 경매**: A·D는 사이트에서 직접 만든 경매 하나(시작가 1,000, 단위 1,000, 마감 3시간 뒤), E는 `seed-close.sql`
- 시작 전에 팀 채널에 공지, 단계 사이 1~2분 쉬기

Git Bash를 열 때마다 먼저 이 한 줄을 실행해 두면, 아래 명령의 `$DEV`가 dev 주소와 비밀값으로 바뀌어요. (`받은값`은 실제 비밀값으로)

```bash
DEV="-e BASE_URL=https://dev.ddangddangddang.com -e JWT_SECRET=받은값"
```

## B. 조회

```bash
k6 run $DEV -e VUS=50  -e HOLD=3m30s --summary-export=results/b1.json browse.js
k6 run $DEV -e VUS=100 -e HOLD=3m30s --summary-export=results/b2.json browse.js
k6 run $DEV -e VUS=150 -e HOLD=3m30s --summary-export=results/b3.json browse.js
```

## A. 입찰

단계마다 `reset.sql` → 새 경매 만들기 (또는 같은 경매로 이어서 하면 reset 없이)

```bash
k6 run $DEV -e AUCTION_ID=번호 -e VUS=50  -e HOLD=3m --summary-export=results/a1.json bid-race.js
k6 run $DEV -e AUCTION_ID=번호 -e VUS=100 -e HOLD=3m --summary-export=results/a2.json bid-race.js
k6 run $DEV -e AUCTION_ID=번호 -e VUS=200 -e HOLD=3m --summary-export=results/a3.json bid-race.js
```

## D. 입찰 + 실시간 + 알림

단계마다 새 경매로 하세요 (알림 지연을 경매별로 재기 때문). 끝나면 `check-notifications.sql`의 `@auction`을 그 경매 번호로 바꿔 실행해요.

```bash
k6 run $DEV -e AUCTION_ID=번호 -e BIDDERS=20 -e WATCHERS=100 -e HOLD=3m --summary-export=results/d1.json realtime-bid.js
k6 run $DEV -e AUCTION_ID=번호 -e BIDDERS=40 -e WATCHERS=200 -e HOLD=3m --summary-export=results/d2.json realtime-bid.js
k6 run $DEV -e AUCTION_ID=번호 -e BIDDERS=60 -e WATCHERS=300 -e HOLD=3m --summary-export=results/d3.json realtime-bid.js
```

볼 것: `ws_connected`, `ws_connect_time`, `ws_errors`, `ws_messages`(대략 입찰 성공 × 구경꾼 수에 가까우면 잘 퍼진 것), 그리고 SQL의 `expected_outbid = actual_outbid`, `max_delay_sec`, `drain_after_last_bid_sec`.

## E. 마감 몰림

단계마다 ① `seed-close.sql`의 `@count`를 10 → 30 → 60으로 바꿔 실행 ② **바로** k6 시작 ③ k6가 끝나고 1~2분 뒤 `check-close.sql` ④ `cleanup-close.sql`

```bash
k6 run $DEV -e CLOSE_COUNT=10 -e VUS=30  -e HOLD=3m30s --summary-export=results/e1.json close-rush.js
k6 run $DEV -e CLOSE_COUNT=30 -e VUS=60  -e HOLD=3m30s --summary-export=results/e2.json close-rush.js
k6 run $DEV -e CLOSE_COUNT=60 -e VUS=100 -e HOLD=3m30s --summary-export=results/e3.json close-rush.js
```

`seed-close.sql`의 마감은 5분 뒤, k6는 4분 30초 동안 입찰해요. 입찰이 멈추고 30초 뒤(마감 직전 연장 규칙) 경매들이 한꺼번에 마감돼요. `check-close.sql`의 5번(묶인 포인트 합)은 A·D에서 쓴 경매가 아직 진행 중이면 그만큼 남아 있는 게 정상이에요.

## F. 로그인 제한

```bash
k6 run -e BASE_URL=https://dev.ddangddangddang.com login-limit.js
```

⚠ 끝나면 **내 IP는 5분 동안 로그인이 막혀요.** 사이트 확인이 필요한 일을 먼저 마치고 하세요.

## 충전 웹훅 동시성 (백엔드 테스트)

dev가 아니라 내 컴퓨터에서 Docker를 켜고 실행해요. (실제 MySQL을 Docker로 띄워 확인)

```bash
./gradlew test --tests "*PointChargeConcurrencyTest"
```

## 끝나면

dev DB에서 `cleanup-all.sql` 실행 → 사이트에서 만든 테스트 경매 정리 → 팀 채널에 공지

---

# 방송 부하 테스트 (`live.js`)

시청자들이 방송에 입장해 채팅·방송 정보·방송 경매를 WebSocket으로 받고, 그중 일부는 채팅을 보내요.
**영상(LiveKit)에는 연결하지 않아요.** 영상은 LiveKit이 감당하고 요금도 나가서, 우리 서버가 감당하는 부분만 재요.

| 우리 서버가 하는 일 | 테스트에서 |
| --- | --- |
| 방송 입장 (시청 토큰 발급) | `POST /live-streams/{id}/join` |
| 방송 정보, 방송에 올라간 경매 | `GET /live-streams/{id}`, `GET /lives/{id}/auctions` |
| WebSocket 연결 유지 | 채팅·방송·방송 경매·오류 토픽 구독 |
| 채팅 퍼뜨리기 | 채팅 1개를 그 방송 시청자 **전원**에게 전송 |
| 채팅 받기 | 도배 검사(5초에 5개), 같은 내용 반복 검사(30초에 2번), 금칙어 검사 |

## 준비

1. **테스트용 방송 하나를 켜요.** 판매자 계정으로 스튜디오에서 방송을 만들고 시작해요. 제목은 "부하 테스트"처럼 알아보기 쉽게. 방송 주소 `/lives/번호`의 번호가 `LIVE_ID`예요
   - 영상을 안 보내면 서버가 **30분 뒤 방송을 자동 종료**해요. 테스트를 그 안에 끝내거나, 카메라로 실제로 송출해 두세요
2. **스튜디오에서 채팅 AI 검토를 꺼요** (끄기/OFF). 켜져 있으면 채팅마다 Claude API 요금이 나가요
3. **방송 화면(스튜디오 포함) 탭은 닫아 두세요.** 채팅이 초당 수십 개씩 쏟아져서 브라우저가 버벅이고, 측정에도 내 브라우저 몫이 섞여요
4. 테스트 회원(`seed.sql`)이 있어야 해요. dev면 `$DEV`(JWT 비밀값)도 필요해요

## 왜 로그인한 회원으로 입장하나

손님(로그인 안 한 사람) 입장은 **IP당 1분에 20번**으로 막혀 있어요. 한 컴퓨터에서 수백 명을 흉내 내면 바로 막혀서, k6가 만든 토큰으로 회원 입장을 해요. 회원 입장은 이 제한에 안 걸려요.

## 채팅은 어떻게 보내나

- 3초에 한 번 (도배 제한 5초에 5개보다 느리게)
- 숫자 없이 한글로만 (숫자가 이어지면 연락처·계좌번호로 막혀요)
- 끝에 메시지마다 다른 한글 두 글자를 붙여 같은 내용 반복 제한을 피하고, 그 글자로 **내가 보낸 채팅이 다시 나에게 도착하는 시간**을 재요. 이게 다른 시청자에게 퍼지는 시간이에요

## 3단계

| 단계 | 시청자 | 채팅하는 사람 | 초당 채팅 | 서버가 내보내는 채팅 (초당) |
| --- | --- | --- | --- | --- |
| 1 | 100 | 10 | 약 3 | 약 370 |
| 2 | 300 | 30 | 약 10 | 약 3,300 |
| 3 | 500 | 50 | 약 17 | 약 9,200 |

```bash
k6 run $DEV -e LIVE_ID=번호 -e VIEWERS=100 -e CHATTERS=10 -e HOLD=3m --summary-export=results/g1.json live.js
k6 run $DEV -e LIVE_ID=번호 -e VIEWERS=300 -e CHATTERS=30 -e HOLD=3m --summary-export=results/g2.json live.js
k6 run $DEV -e LIVE_ID=번호 -e VIEWERS=500 -e CHATTERS=50 -e HOLD=3m --summary-export=results/g3.json live.js
```

로컬에서는 `$DEV` 없이 실행해요. 시청자 + 채팅하는 사람 수가 `seed.sql`의 입찰자 수(500)를 넘으면 안 돼서, 3단계는 `seed.sql`의 `@bidders`를 600 이상으로 올려 다시 실행해 두세요.

## 입장 몰림 (방송 시작 알림 직후)

알림을 받은 사람들이 몇 초 안에 한꺼번에 들어오는 상황이에요. 늘리기 시간을 10초로 줄여요.

```bash
k6 run $DEV -e LIVE_ID=번호 -e VIEWERS=300 -e CHATTERS=1 -e ENTRY_RAMP=10s -e HOLD=1m --summary-export=results/g-burst.json live.js
```

## 목표와 볼 것

| 항목 | 목표 |
| --- | --- |
| `live_join` p95 | 0.5초 이하 |
| `live_detail` p95 | 0.3초 이하 |
| `ws_connected` | 99% 이상 |
| `chat_echo_time` p95 (채팅이 퍼지는 시간) | 1초 이하 |
| `chat_errors` | 10개 미만 (도배·금칙어로 막힌 채팅) |
| `chat_received` | 대략 `chat_sent × (시청자 + 채팅하는 사람)`에 가까울 것. 들어오고 나가는 시간이 있어 조금 적은 게 정상 |

`chat_received`가 기대값보다 **크게** 적거나 `chat_echo_time`이 몇 초씩 늘어나면, 서버가 채팅을 다 못 내보내고 밀리고 있다는 뜻이에요.

## 참고

- 사이트의 **시청자 수 표시는 안 늘어나요.** 시청자 수는 LiveKit에 영상으로 연결한 사람 수로 세는데, 테스트는 영상에 연결하지 않아서예요
- 테스트 채팅은 실제 그 방송 채팅창에 보여요. 다른 팀원이 보고 있는 방송으로는 하지 마세요
- 끝나면 방송을 종료하고, 팀 채널에 공지하세요

---

# 종합 시나리오: 인기 방송 (`showtime.js`)

실제로 가장 위험한 순간을 한 번에 재현해요. 따로 잴 때는 괜찮던 기능들이 **같은 시간에 몰리면** 서로 DB 연결·스레드·WebSocket 전송을 나눠 쓰면서 숨어 있던 병목이 드러나요.

```
방송 시작 알림 → 시청자 몰려 입장 → 채팅 → 방송 경매를 보며 실시간 수신 → 마감 직전 입찰 몰림 → 한꺼번에 마감(주문·정산·알림)
```

## 시간표 (MINUTES=6)

| 시각 | 일어나는 일 |
| --- | --- |
| 방송 시작 순간 | 구독자 전원에게 "방송 시작" 알림 (`seed-showtime-subs.sql`(구독) + `seed-showtime-auctions.sql`(경매)이 구독을 넣어 둠) |
| 0:00~0:10 | 시청자 몰려 입장 (`ENTRY_RAMP`) |
| 0:00~끝 | 채팅, 방송·방송 경매 토픽 수신, 일부는 경매 상세를 실시간으로 봄 |
| 3:30~5:45 | 입찰자들이 방송 경매에 입찰 (마감 2분 30초 전부터 15초 전까지) |
| 약 6:15 | 마지막 입찰 30초 뒤 방송 경매들이 한꺼번에 마감 |
| 약 7:25 | 시청자 퇴장, 테스트 끝 |

## 순서 (꼭 이 순서로)

1. **판매자 계정으로 방송을 만들기만** 해요 (시작 X). 스튜디오에서 채팅 AI 검토를 끄고, 방송 번호(`/lives/번호`)를 확인해요
2. `seed-showtime-subs.sql`(구독) + `seed-showtime-auctions.sql`(경매) 맨 위의 `@live`를 방송 번호로 바꾸고 실행 (구독 500명 + 방송 경매 5개, 6분 뒤 마감)
3. **스튜디오에서 방송 시작** → 이 순간 "방송 시작" 알림이 구독자 500명에게 몰려요
4. **바로** k6 실행 (경매가 6분 뒤 마감이라 늦으면 시간표가 밀려요)

```bash
k6 run -e LIVE_ID=번호숫자 -e MINUTES=6 -e VIEWERS=200 -e CHATTERS=20 -e BIDDERS=40 \
  --summary-export=results/showtime-2.json showtime.js 2>&1 | tee results/showtime-2.log
```

5. k6가 끝나고 1~2분 뒤 `check-showtime.sql` (`@live` 바꾸고)
6. `cleanup-showtime.sql` (`@live` 바꾸고), 방송 종료, 사이트에서 정리

dev에서는 `k6 run $DEV ...`로, SQL은 IntelliJ의 `ddang-dev`에서 실행해요.

## 3단계

| 단계 | 시청자 | 채팅 | 경매 구경 | 입찰자 | 합계 (테스트 회원) |
| --- | --- | --- | --- | --- | --- |
| 1 | 100 | 10 | 20 | 20 | 150 |
| 2 | 200 | 20 | 40 | 40 | 300 |
| 3 | 400 | 40 | 80 | 60 | 580 |

- 합계가 `seed.sql`의 `@bidders`(500)를 넘으면 안 돼요. 3단계는 `@bidders`를 600 이상으로 올려 다시 실행하세요
- 경매 구경 인원은 기본으로 시청자의 5분의 1이에요 (`AUCTION_WATCHERS`로 바꿀 수 있음)
- **단계마다 새 방송**으로 해요. "방송 시작" 알림은 방송을 시작하는 순간에만 나가서, 같은 방송으로 다시 하면 알림 몰림이 재현되지 않아요. 단계 사이에는 `cleanup-showtime.sql` → 방송 종료 → 새 방송 만들기 → `seed-showtime-subs.sql`(구독) + `seed-showtime-auctions.sql`(경매) → 시작 → k6

## 목표

| 항목 | 목표 | 어디서 보나 |
| --- | --- | --- |
| 방송 입장 p95 | 0.5초 이하 | k6 `live_join` |
| 입찰 p95 | 1초 이하 | k6 `bid` |
| WebSocket 연결 | 99% 이상 | k6 `ws_connected` |
| 채팅 퍼지는 시간 p95 | 1초 이하 | k6 `chat_echo_time` |
| 예상 못 한 오류 | 입찰·채팅 각 10건 미만 | k6 `bid_unexpected`, `chat_errors` |
| 방송 시작 알림 | 구독자 수만큼, 10초 안에 | SQL 1번 `live_started_notifications`, `fanout_sec` |
| 방송 경매 마감 | 전부 ENDED/FAILED, 주문 = 낙찰, 마감→주문 10초 이하 | SQL 2·3번 |
| 상위 입찰 알림 | 기대 = 실제, 입찰이 멈추고 5초 안에 다 처리 | SQL 4번 |

같은 시간대 **서버 CPU, DB CPU·연결 수**(dev는 CloudWatch)를 같이 받아두면, 어디가 먼저 버거워졌는지 말할 수 있어요.

## 100명 · 200명 · 300명 비교 표 만들기

`TOTAL`로 동시 사용자 합계만 주면 역할을 알아서 나눠요. (시청자 65% / 채팅 7% / 경매 구경 13% / 입찰자 15%)

| TOTAL | 시청자 | 채팅 | 경매 구경 | 입찰자 |
| --- | --- | --- | --- | --- |
| 100 | 65 | 7 | 13 | 15 |
| 200 | 130 | 14 | 26 | 30 |
| 300 | 195 | 21 | 39 | 45 |

단계마다 새 방송으로 (cleanup → 방송 종료 → 새 방송 → seed-showtime → 시작 → k6):

```bash
k6 run -e LIVE_ID=번호숫자 -e MINUTES=6 -e TOTAL=100 --summary-export=results/showtime-100.json showtime.js
k6 run -e LIVE_ID=번호숫자 -e MINUTES=6 -e TOTAL=200 --summary-export=results/showtime-200.json showtime.js
k6 run -e LIVE_ID=번호숫자 -e MINUTES=6 -e TOTAL=300 --summary-export=results/showtime-300.json showtime.js
```

세 번 다 끝나면 표로 모아요. (Node만 있으면 돼요, 프론트 때문에 이미 설치돼 있어요)

```bash
node make-table.mjs results/showtime-100.json results/showtime-200.json results/showtime-300.json
```

화면에 마크다운 표가 나오고 `results/table.md`에도 저장돼요. 노션에 붙여 넣으면 그대로 표가 돼요. 같은 방법으로 다른 시나리오 결과 파일도 표로 만들 수 있어요 (그 시나리오에 없는 항목은 `-`나 `0`으로 나와요).
