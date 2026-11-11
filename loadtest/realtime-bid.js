// 시나리오 D. 입찰 + 실시간: 입찰자 BIDDERS명이 같은 경매에 입찰하는 동안, 구경꾼 WATCHERS명이 그 경매를 WebSocket으로 보고 있다.
// 입찰 한 번이 구경꾼 전원에게 실시간 메시지로 퍼지는 부담과, "상위 입찰자 나옴" 알림(@Async)이 밀리는지를 본다.
// 실행: k6 run -e AUCTION_ID=경매번호 -e BIDDERS=20 -e WATCHERS=100 realtime-bid.js
// 알림이 밀렸는지는 끝난 뒤 check-notifications.sql로 본다.
// 메시지 도달: 결과의 ws_messages가 대략 bid_success × 구경꾼 수에 가까우면 잘 퍼진 것 (들어오고 나가는 시간만큼 조금 적은 게 정상)
import { bidOnce, prepareTokens, stagesFor, watchAuction, AUCTION_ID } from './common.js';

const BIDDERS = Number(__ENV.BIDDERS || 20);
const WATCHERS = Number(__ENV.WATCHERS || 100);
// 구경꾼 한 명이 한 번 연결해 머무는 시간(초). 끝나면 다시 연결한다 (페이지를 다시 연 사람)
const WATCH_SECONDS = Number(__ENV.WATCH_SECONDS || 60);

export const options = {
  scenarios: {
    bidders: { executor: 'ramping-vus', startVUs: 0, stages: stagesFor(BIDDERS), gracefulRampDown: '10s', exec: 'bidders' },
    watchers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: stagesFor(WATCHERS),
      gracefulRampDown: `${WATCH_SECONDS + 5}s`,
      gracefulStop: `${WATCH_SECONDS + 5}s`,
      exec: 'watchers',
    },
  },
  thresholds: {
    'http_req_duration{name:bid}': ['p(95)<1000'],
    bid_unexpected: ['count<10'],
    ws_connected: ['rate>0.99'], // 구경꾼 99% 이상 연결·구독 성공
    ws_errors: ['count<10'],
    ws_connect_time: ['p(95)<1000'],
  },
};

export function setup() {
  return prepareTokens(BIDDERS + WATCHERS); // VU 번호는 구경꾼까지 통틀어 매겨져서, 입찰자끼리 회원이 겹치지 않게 전체만큼 만든다
}

export function bidders(data) {
  bidOnce(data);
}

export function watchers() {
  watchAuction(AUCTION_ID, WATCH_SECONDS);
}
