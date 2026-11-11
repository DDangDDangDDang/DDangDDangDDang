// 종합 시나리오: 인기 방송
// 방송 시작 → 시청자 몰려 입장 → 채팅 → 방송 경매를 보며 실시간 수신 → 마감 직전 입찰 몰림 → 한꺼번에 마감(주문·정산·알림)
// 이 모든 게 같은 시간에 겹칠 때 서버가 버티는지 본다.
//
// 순서: 방송 만들기(시작 X) → seed-showtime.sql → 스튜디오에서 방송 시작 → 바로 이 스크립트
// 실행: k6 run -e LIVE_ID=방송번호 -e MINUTES=6 -e VIEWERS=200 -e CHATTERS=20 -e BIDDERS=40 showtime.js
//   MINUTES는 seed-showtime.sql의 @minutes와 같게 (경매 마감까지 남은 분)
// 결과: k6 요약 + 끝나고 1~2분 뒤 check-showtime.sql
//
// 시간표 (MINUTES=6 기준)
//   0:00        시청자 입장 몰림 (ENTRY_RAMP 동안 VIEWERS명), 채팅 시작
//   0:00~끝     시청자는 채팅·방송·방송 경매 토픽을 받고, AUCTION_WATCHERS명은 경매 상세를 실시간으로 본다
//   3:30~5:45   입찰자 BIDDERS명이 방송 경매에 입찰 (마감 2분 30초 전부터 15초 전까지)
//   ~6:15       마지막 입찰 30초 뒤 경매들이 한꺼번에 마감 (마감 직전 연장 규칙)
//   ~7:00       시청자 퇴장
import { sleep } from 'k6';
import { bidOnce, enterLive, prepareTokens, requireNumber, watchAuction, watchLive } from './common.js';

const LIVE_ID = __ENV.LIVE_ID;
const MINUTES = Number(__ENV.MINUTES || 6);
// TOTAL(동시 사용자 합계)만 주면 역할을 나눠 준다: 시청자 65% / 채팅 7% / 경매 구경 13% / 입찰자 15%
// 예) TOTAL=100 → 65 / 7 / 13 / 15, TOTAL=300 → 195 / 21 / 39 / 45. 역할별로 직접 정하려면 VIEWERS 등을 준다
const TOTAL = Number(__ENV.TOTAL || 0);
const share = (ratio, fallback) => (TOTAL > 0 ? Math.max(1, Math.round(TOTAL * ratio)) : fallback);
const VIEWERS = Number(__ENV.VIEWERS || share(0.65, 200));
const CHATTERS = Number(__ENV.CHATTERS || share(0.07, 20));
const AUCTION_WATCHERS = Number(__ENV.AUCTION_WATCHERS || share(0.13, Math.round(VIEWERS / 5)));
const BIDDERS = Number(__ENV.BIDDERS || share(0.15, 40));
const USERS = VIEWERS + CHATTERS + AUCTION_WATCHERS + BIDDERS;
const AUCTION_COUNT = Number(__ENV.AUCTION_COUNT || 5); // seed-showtime.sql의 @auctions
const ENTRY_RAMP = __ENV.ENTRY_RAMP || '10s';
const CHAT_EVERY = Number(__ENV.CHAT_EVERY || 3);
const WATCH_SECONDS = 60;
const FIRST_AUCTION_ID = 9200001;

// 마감까지 MINUTES분. 입찰은 마감 150초 전부터 15초 전까지. 시청은 마감 후 75초까지
const END_SEC = MINUTES * 60;
const BID_START_SEC = Math.max(0, END_SEC - 150);
const BID_DURATION_SEC = END_SEC - 15 - BID_START_SEC;
const WATCH_TOTAL_SEC = END_SEC + 75;
const s = (sec) => `${Math.round(sec)}s`;

const watching = (target, exec) => ({
  executor: 'ramping-vus',
  startVUs: 0,
  stages: [
    { duration: ENTRY_RAMP, target },
    { duration: s(WATCH_TOTAL_SEC - 10), target },
    { duration: '10s', target: 0 },
  ],
  gracefulRampDown: `${WATCH_SECONDS + 5}s`,
  gracefulStop: `${WATCH_SECONDS + 5}s`,
  exec,
});

export const options = {
  // 시청자는 한 번 들어오면 60초 동안 HTTP를 안 쓰다가 다시 입장한다. 그 사이 서버(톰캣)가 쉬고 있는 연결을 닫는데,
  // k6가 그 닫힌 연결을 다시 쓰려다 EOF로 실패하는 경우가 생긴다. 다시 입장할 때마다 새 연결을 쓰게 한다. (브라우저도 새로 연결함)
  noVUConnectionReuse: true,
  scenarios: {
    viewers: watching(VIEWERS, 'viewers'),
    chatters: watching(CHATTERS, 'chatters'),
    auction_watchers: watching(AUCTION_WATCHERS, 'auctionWatchers'),
    bidders: {
      executor: 'ramping-vus',
      startTime: s(BID_START_SEC),
      startVUs: 0,
      stages: [
        { duration: '15s', target: BIDDERS },
        { duration: s(BID_DURATION_SEC - 15), target: BIDDERS },
      ],
      gracefulRampDown: '5s',
      gracefulStop: '5s',
      exec: 'bidders',
    },
  },
  thresholds: {
    'http_req_duration{name:live_join}': ['p(95)<500'],
    'http_req_duration{name:live_detail}': ['p(95)<300'],
    'http_req_duration{name:bid}': ['p(95)<1000'],
    'http_req_failed{name:live_join}': ['rate<0.01'],
    ws_connected: ['rate>0.99'],
    ws_connect_time: ['p(95)<1000'],
    chat_echo_time: ['p(95)<1000'],
    chat_errors: ['count<10'],
    bid_unexpected: ['count<10'],
  },
};

export function setup() {
  requireNumber('LIVE_ID', LIVE_ID);
  // 테스트 회원을 역할별로 나눠 쓴다: 시청자 | 채팅 | 경매 구경 | 입찰자
  return prepareTokens(USERS, false);
}

const pickAuction = () => FIRST_AUCTION_ID + Math.floor(Math.random() * AUCTION_COUNT);

// 테스트 회원은 VU(가상 사용자) 번호로 한 명씩 정해 준다. k6의 VU 번호는 모든 역할을 통틀어 1부터 겹치지 않으므로,
// 역할별로 나머지 연산을 하면 두 VU가 같은 회원을 쓰게 되고, 한 회원이 채팅을 두 배로 보내 도배 제한(5초에 5개)에 걸린다.
const tokenOf = (data) => data.tokens[(__VU - 1) % data.tokens.length];

export function viewers(data) {
  const token = tokenOf(data);
  if (!enterLive(LIVE_ID, token)) {
    sleep(1);
    return;
  }
  watchLive(LIVE_ID, token, WATCH_SECONDS);
}

export function chatters(data) {
  const token = tokenOf(data);
  if (!enterLive(LIVE_ID, token)) {
    sleep(1);
    return;
  }
  watchLive(LIVE_ID, token, WATCH_SECONDS, CHAT_EVERY);
}

// 방송을 보다가 경매 상세를 연 사람 (경매 토픽 구독)
export function auctionWatchers() {
  watchAuction(pickAuction(), WATCH_SECONDS);
}

// 입찰자: 방송 경매 중 하나에 입찰
export function bidders(data) {
  bidOnce(data, pickAuction()); // bidOnce도 VU 번호로 회원을 정한다
}
