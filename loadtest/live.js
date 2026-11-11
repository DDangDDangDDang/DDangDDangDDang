// 시나리오 G. 방송: 시청자 VIEWERS명이 방송에 입장해 채팅을 보고, 그중 CHATTERS명은 채팅을 보낸다.
// 영상(LiveKit)에는 연결하지 않는다. 영상은 LiveKit이 감당하고, 우리 서버는 입장·방송 정보·WebSocket·채팅을 감당한다.
// 실행: k6 run -e LIVE_ID=방송번호 -e VIEWERS=100 -e CHATTERS=10 live.js
// 입장 몰림(방송 시작 알림 직후): -e ENTRY_RAMP=10s 로 늘리기 시간을 짧게
import { sleep } from 'k6';
import { enterLive, HOLD, prepareTokens, requireNumber, watchLive } from './common.js';

const LIVE_ID = __ENV.LIVE_ID;
const VIEWERS = Number(__ENV.VIEWERS || 100);
const CHATTERS = Number(__ENV.CHATTERS || 10);
const WATCH_SECONDS = Number(__ENV.WATCH_SECONDS || 60); // 한 번 들어와 머무는 시간. 끝나면 다시 입장 (나갔다 들어온 사람)
const CHAT_EVERY = Number(__ENV.CHAT_EVERY || 3); // 채팅 간격(초). 도배 제한이 5초에 5개라 2 이상
const ENTRY_RAMP = __ENV.ENTRY_RAMP || '30s';

const stages = (target) => [
  { duration: ENTRY_RAMP, target },
  { duration: HOLD, target },
  { duration: '30s', target: 0 },
];

export const options = {
  // 시청자는 한 번 들어오면 60초 동안 HTTP를 안 쓰다가 다시 입장한다. 그 사이 서버(톰캣)가 쉬고 있는 연결을 닫는데,
  // k6가 그 닫힌 연결을 다시 쓰려다 EOF로 실패하는 경우가 생긴다. 다시 입장할 때마다 새 연결을 쓰게 한다. (브라우저도 새로 연결함)
  noVUConnectionReuse: true,
  scenarios: {
    viewers: {
      executor: 'ramping-vus', startVUs: 0, stages: stages(VIEWERS), exec: 'viewers',
      gracefulRampDown: `${WATCH_SECONDS + 5}s`, gracefulStop: `${WATCH_SECONDS + 5}s`,
    },
    chatters: {
      executor: 'ramping-vus', startVUs: 0, stages: stages(CHATTERS), exec: 'chatters',
      gracefulRampDown: `${WATCH_SECONDS + 5}s`, gracefulStop: `${WATCH_SECONDS + 5}s`,
    },
  },
  thresholds: {
    'http_req_duration{name:live_join}': ['p(95)<500'],
    'http_req_duration{name:live_detail}': ['p(95)<300'],
    'http_req_duration{name:live_auctions}': ['p(95)<500'],
    // WebSocket 연결(응답 101)도 http_req_failed에 실패로 잡혀서, 입장 API만 따로 본다
    'http_req_failed{name:live_join}': ['rate<0.01'],
    'http_req_failed{name:live_detail}': ['rate<0.01'],
    ws_connected: ['rate>0.99'],
    ws_connect_time: ['p(95)<1000'],
    chat_echo_time: ['p(95)<1000'],
    chat_errors: ['count<10'],
  },
};

export function setup() {
  requireNumber('LIVE_ID', LIVE_ID);
  // 로그인한 회원으로 입장한다. 손님 입장은 IP당 1분에 20번으로 막혀 있어 부하 테스트로는 쓸 수 없다
  return prepareTokens(VIEWERS + CHATTERS, false);
}

// 테스트 회원은 VU 번호로 한 명씩 정한다. (역할별 나머지 연산을 하면 두 VU가 같은 회원을 써서 도배 제한에 걸린다)
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
