# 부하 테스트 (k6)

경매·입찰·방송 채팅이 사람이 몰릴 때 버티는지 [k6](https://grafana.com/docs/k6/latest/)로 확인한다.

| 시나리오 | 파일 | 무엇을 보나 |
| --- | --- | --- |
| 조회 | `scenarios/browse.js` | 비로그인 방문자가 방송 홈 → 경매 목록·인기 → 경매 상세·입찰 내역을 둘러볼 때 응답 시간과 오류율 |
| 입찰 | `scenarios/bid.js` | 여러 회원이 한 경매(또는 여러 경매)에 동시에 입찰할 때 응답 시간, 락 경합(`AUCTION_018`), 끝난 뒤 현재가 정합성 |
| 채팅 | `scenarios/chat.js` | 시청자 N명이 STOMP로 한 방송 채팅에 붙고 일부가 말할 때 연결 성공률, 메시지 전달 지연, 전달 누락 |

## 1. 준비물

- k6 설치: `winget install k6` (또는 `choco install k6`). `k6 version`으로 확인
- MySQL, Redis (로컬 개발 환경과 같음)

## 2. 서버를 `loadtest` 프로필로 켜기

`loadtest` 프로필은 시작할 때 테스트 데이터를 만들고(`LoadTestDataInitializer`) 회원별 Access Token을 `load-test/data/seed.json`에 남긴다.
로그인 API가 IP당 5분 30회로 막혀 있어 k6는 로그인하지 않고 이 토큰을 쓴다. `prod` 프로필과 함께 켜면 데이터 준비 클래스는 등록되지 않는다.

평소 쓰는 로컬 DB를 어지럽히지 않게 **별도 DB(`ddang_loadtest`)와 Redis 1번 DB**를 쓰는 것을 권장한다.

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/ddang_loadtest?createDatabaseIfNotExist=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
.\gradlew bootRun --args="--server.port=8090 --spring.profiles.active=loadtest --spring.data.redis.database=1"
```

로그에 `[부하 테스트] 데이터 준비 완료`가 찍히면 준비 끝이다. 만들어지는 것:

- 입찰자 회원 200명(`loadtest-001@ddang.test` …, 포인트 1억씩)과 판매자 1명(`loadtest-seller@ddang.test`)
- 진행 중 경매 5개 (시작가 1,000 / 단위 100 / 6시간). **서버를 켤 때마다 새로 5개** 만든다
- 판매자의 방송 1개 (LIVE 상태, LiveKit 송출 없음. 자동 종료 감시는 꺼 둠)

회원 비밀번호는 무작위라 화면에서 로그인할 수는 없다.

| 환경 변수 | 기본값 | 뜻 |
| --- | --- | --- |
| `LOADTEST_USERS` | 200 | 입찰자 회원 수 (채팅 접속자보다 적어도 됨, 같은 회원이 여러 번 접속) |
| `LOADTEST_POINTS` | 100000000 | 회원별 포인트 목표치 (모자라면 채움) |
| `LOADTEST_AUCTIONS` | 5 | 새로 만들 경매 수 |
| `LOADTEST_AUCTION_HOURS` | 6 | 경매 진행 시간 |
| `LOADTEST_TOKEN_VALIDITY` | 6h | Access Token 유효 시간. 테스트가 이보다 길면 서버를 다시 켜서 토큰을 새로 받는다 |
| `LOADTEST_OUTPUT` | `load-test/data/seed.json` | 시드 파일 위치 (bootRun 작업 폴더 기준) |

## 3. 실행

먼저 작게 돌려 스크립트·서버가 맞는지 본다(스모크). 그다음 숫자를 올린다.

```bash
# 조회: 최대 동시 사용자 VUS명, RAMP 동안 늘리고 DURATION 동안 유지
k6 run -e BASE_URL=http://localhost:8090 -e VUS=10 -e RAMP=5s -e DURATION=20s load-test/scenarios/browse.js
k6 run -e BASE_URL=http://localhost:8090 -e VUS=300 load-test/scenarios/browse.js

# 입찰: 초당 RATE번 입찰 시도. MODE=hot(한 경매에 몰림, 기본) / spread(경매 5개에 나눔)
k6 run -e BASE_URL=http://localhost:8090 -e RATE=20 -e RAMP=5s -e DURATION=20s load-test/scenarios/bid.js
k6 run -e BASE_URL=http://localhost:8090 -e RATE=200 load-test/scenarios/bid.js

# 채팅: VIEWERS명 접속, 그중 SENDERS명이 SEND_INTERVAL초마다 말함, DURATION초 유지
k6 run -e BASE_URL=http://localhost:8090 -e VIEWERS=30 -e SENDERS=5 -e DURATION=20 load-test/scenarios/chat.js
k6 run -e BASE_URL=http://localhost:8090 -e VIEWERS=1000 -e SENDERS=50 -e DURATION=180 load-test/scenarios/chat.js
```

결과를 파일로 남기려면 `--summary-export load-test/results/<이름>.json`을 붙인다. (`results/`, `data/`는 git에 올라가지 않는다)

한계를 찾을 때는 한 번에 크게 올리지 말고 단계적으로(예: 입찰 50 → 100 → 200 → 400/s) 올리며, 처음으로 임계값(threshold)이 깨지는 지점을 기록한다.

### 결과 읽는 법

- **http_req_failed**: 5xx·연결 실패 비율. 입찰의 400/409(최소가 미달, 이미 최고 입찰자, 락 충돌)는 경쟁에서 진 정상 응답이라 실패로 세지 않는다
- **bid_result{code:…}**: 입찰 결과 코드별 개수. `OK`가 성공, `AUCTION_016`(최소가 미달)·`AUCTION_015`(이미 최고 입찰자)는 경쟁 패배, `AUCTION_018`(BID_CONFLICT)은 서버가 락 재시도 5번을 다 쓰고 포기한 것 → 늘어나면 입찰 처리의 한계
- 입찰 테스트가 끝나면 `현재가 = 시작가 + 단위 × (입찰 수 - 1)`을 확인해 콘솔에 `정합성 OK / 불일치!`를 찍는다
- **chat_echo_latency**: 보낸 메시지가 자기에게 돌아오기까지 걸린 시간. **chat_received**는 이상적으로 `chat_sent × VIEWERS`
- **chat_error**: 서버가 `/user/queue/errors`로 보낸 오류 (도배 제한: 5초에 5개, 같은 말 30초에 2번)

포인트 홀딩 정합성은 SQL로 따로 본다. 진행 중 경매의 최고 입찰자 홀딩 합계와 지갑 `held_balance` 합계가 같아야 한다.

```sql
SELECT (SELECT COALESCE(SUM(current_price), 0) FROM auctions WHERE status = 'ONGOING' AND bid_count > 0) AS expected_held,
       (SELECT SUM(held_balance) FROM point_wallets) AS actual_held;
```

## 4. 모니터링: Prometheus + Grafana

k6 결과만으로는 "언제 느려졌나"까지만 안다. **왜** 느려졌는지는 같은 시각의 서버 내부 지표(커넥션 풀, 스레드, GC, CPU)를 함께 봐야 한다.

```
Spring 서버 ──/actuator/prometheus──▶ Prometheus ◀──remote write── k6
 (Micrometer가 지표 수집)         (5초마다 가져와 저장)          (테스트 결과를 밀어 넣음)
                                        │
                                        ▼
                                     Grafana (대시보드 "DDang 부하 테스트")
```

- 서버: `loadtest` 프로필에서만 `/actuator/metrics`, `/actuator/prometheus`를 노출하고 토큰 없이 열어 둔다(`LoadTestMetricsSecurityConfig`). 기본·운영 설정은 지금처럼 health만 노출한다.
- Prometheus는 `host.docker.internal:8090`을 수집한다. 서버를 다른 포트로 켰다면 `monitoring/prometheus.yml`을 고친다.

### 켜기

Docker Desktop이 켜져 있어야 한다.

```bash
docker compose -f load-test/monitoring/docker-compose.yml up -d
```

- Grafana: http://localhost:3000 (로그인 없이 열림, 홈이 "DDang 부하 테스트" 대시보드)
- Prometheus: http://localhost:9090 (Status → Targets에서 `ddang-app`이 UP인지 확인)

### k6 결과를 Prometheus로 보내기

`-o experimental-prometheus-rw`를 붙이고, 응답 시간은 어떤 통계를 보낼지 정한다. (PowerShell)

```powershell
$env:K6_PROMETHEUS_RW_SERVER_URL = "http://localhost:9090/api/v1/write"
$env:K6_PROMETHEUS_RW_TREND_STATS = "p(95),p(99),avg,max"
k6 run -o experimental-prometheus-rw -e BASE_URL=http://localhost:8090 -e RATE=200 load-test/scenarios/bid.js
```

### 대시보드 읽는 법

| 구역 | 패널 | 이렇게 보이면 |
| --- | --- | --- |
| k6 | 초당 요청 수, 응답 시간 p95, VU·실패율 | 부하를 올려도 초당 요청 수가 더 안 오르고 p95만 오르면 한계에 닿은 것 |
| k6 | 입찰 결과 코드 | `AUCTION_018`이 나타나기 시작하는 RATE가 입찰 처리 한계 |
| k6 | 채팅 전달 지연·수신 | 지연이 오르거나 초당 수신이 `보낸 수 × 접속자 수`보다 적으면 브로드캐스트 병목·유실 |
| 서버 HTTP | API별 요청 수·p95, 상태 코드 | k6 p95는 높은데 서버 p95는 낮으면 서버 밖(네트워크, 연결 대기, k6 PC) 문제 |
| HikariCP | 사용 현황 | `active`가 `max`(10)에 붙고 `pending`이 0보다 크면 **커넥션 풀이 병목** |
| HikariCP | 얻는 데 걸린 시간, 쥐고 있던 시간 | 쥐고 있던 시간이 길면 트랜잭션이 오래 걸리는 것(락 대기 등). 얻는 시간이 길면 풀이 모자란 것 |
| Tomcat·JVM | 요청 스레드 | `busy`가 최대(200)에 붙으면 요청이 줄을 선다 |
| Tomcat·JVM | JVM 스레드 상태 | `blocked`·`waiting`이 늘면 락·커넥션을 기다리는 스레드가 많다 → 그 순간 `jcmd <PID> Thread.print`로 스레드 덤프 |
| Tomcat·JVM | CPU | `process`(서버)가 높으면 서버 연산 병목. 로컬은 `system`(PC 전체)에 k6도 포함됨 |
| Tomcat·JVM | 힙, GC 멈춤 | 힙이 최대 근처에서 안 내려오고 GC 멈춤이 늘면 메모리 부족 |

병목은 "가장 먼저 꽉 찬 자원"이다. 응답 시간이 꺾이는 시각에 위 패널 중 무엇이 먼저 한계에 닿았는지 본다.

### DB·Redis는 따로 본다

이 대시보드에는 MySQL·Redis 내부 지표가 없다. 테스트 중에 직접 조회한다.

```sql
SELECT * FROM sys.innodb_lock_waits;            -- 지금 누가 누구의 락을 기다리나
SHOW GLOBAL STATUS LIKE 'Innodb_row_lock%';     -- 누적 락 대기 (테스트 전후 비교)
SHOW FULL PROCESSLIST;                          -- 실행 중인 쿼리
SHOW ENGINE INNODB STATUS;                      -- 마지막 데드락
```

```bash
docker exec ddang-redis redis-cli INFO stats
```

### 끄기

```bash
docker compose -f load-test/monitoring/docker-compose.yml down
```

수집한 데이터까지 지우려면 `down -v`를 쓴다.

## 5. 대상 환경 주의

- **로컬**: k6와 서버가 같은 PC에서 CPU를 나눠 쓰므로 절대 수치보다 병목 위치·오류 양상을 보는 용도
- **개발 서버(dev.ddangddangddang.com)**: `loadtest` 프로필을 켜지 않는다. 개발 서버는 `prod` 프로필로 뜨므로(`infra/dev.tf`) 함께 켜면 시드·지표 보안 설정은 빠지고, 긴 토큰·방송 자동 종료 끔·지표 노출만 실제 사용자에게 적용된다. 개발 서버에서 테스트하려면 시드 데이터를 만드는 방법을 따로 정해야 한다. 또 EC2 1대에 web·app·MySQL·Redis가 함께 떠 있어(메모리 2GB) 운영 구성과 다르다
- **운영(ddangddangddang.com)**: 실제 회원·데이터가 있으므로 하지 않는다. 스크립트가 운영 주소를 막는다(`ALLOW_PROD=true`로만 풀림)
- 서버 쪽 지표는 4장의 Grafana 대시보드로 함께 본다. 개발 서버를 테스트할 때는 그 서버의 Actuator가 열려 있지 않으므로 CloudWatch와 `docker stats`로 본다

## 6. 정리

별도 DB를 썼다면 DB째 지운다: `DROP DATABASE ddang_loadtest;` 와 Redis 1번 DB `redis-cli -n 1 FLUSHDB`.
평소 DB에 만들었다면 `loadtest-`로 시작하는 회원과 그 판매자의 상품·경매·방송을 지운다.
