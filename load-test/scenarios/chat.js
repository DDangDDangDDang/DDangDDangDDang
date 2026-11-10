// 채팅 부하: 시청자 여러 명이 한 방송 채팅방에 WebSocket(STOMP)으로 붙고, 그중 일부가 메시지를 보낸다.
// 실행: k6 run -e BASE_URL=http://localhost:8080 load-test/scenarios/chat.js
// 조절: VIEWERS(접속자 수, 기본 200), SENDERS(그중 보내는 사람 수, 기본 20), SEND_INTERVAL(보내는 간격 초, 기본 3),
//       DURATION(접속 유지 초, 기본 120), CONNECT_SPREAD(접속 시작을 흩는 초, 기본 0 = 모두 동시에),
//       JOIN_GRACE(테스트 시작부터 보내기 시작까지 최소 몇 초를 둘지, 기본 5)
//
// 진행 순서: 접속(CONNECT_SPREAD 안에서 흩음) → 접속 대기가 끝나는 시각(max(CONNECT_SPREAD, JOIN_GRACE))부터 보내기 시작
//            → DURATION 동안 함께 머묾 → 모두 같은 시각에 보내기를 멈추고 2초 뒤 나감
//
// 보는 것
// - chat_echo_latency: 보낸 사람이 자기 메시지를 구독 경로로 다시 받기까지 걸린 시간 (서버 처리 + 브로드캐스트).
//   보낸 사람 기준이라 다른 시청자 전원의 전달 지연과는 다를 수 있다
// - chat_received: 받은 채팅 수 합계. 기대값은 chat_sent × chat_ready 다.
//   chat_late(보내기 시작 뒤에야 연결됨)나 chat_dropped(끝나기 전에 끊김)가 0이 아니면 그 사람들은 일부만 받으므로
//   합계가 기대값보다 적게 나온다. 유실로 보기 전에 이 둘을 먼저 본다. 합계만 보므로 사람별 누락·중복은 가리지 못한다
// - chat_ready / chat_late / chat_dropped: 보내기 시작 시각에 연결돼 있던 사람 / 그 뒤에 연결된 사람 / 도중에 끊긴 사람 수.
//   VIEWERS명 전원이 함께 있었는지는 chat_ready = VIEWERS 인지로 본다 (연결 실패한 사람은 셋 어디에도 들어가지 않는다)
//   구독 완료는 확인하지 못한다(내장 브로커가 RECEIPT를 주지 않음). 보내기 시작 시각을 테스트 시작 뒤
//   max(CONNECT_SPREAD, JOIN_GRACE)초로 고정해 그 여유로 둔다. 그 시각 직전에 연결된 사람은 여유가 짧을 수 있다
// - chat_error: /user/queue/errors로 받은 오류 (도배 제한 등)
// - ws_connecting, stomp_connected_rate: 접속이 몰릴 때 연결이 버티는지
// 서버 도배 제한은 5초에 5개, 같은 말 30초에 2번이라 SEND_INTERVAL을 1초 아래로 내리면 오류가 섞인다.
import { WebSocket } from 'k6/websockets';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { WS_URL, guardTarget } from '../lib/common.js';

const seed = JSON.parse(open(__ENV.SEED || '../data/seed.json'));
const users = new SharedArray('users', () => seed.users);
const LIVE_STREAM_ID = Number(__ENV.LIVE_STREAM_ID || seed.liveStreamId);
const VIEWERS = Number(__ENV.VIEWERS || 200);
const SENDERS = Number(__ENV.SENDERS || 20);
const SEND_INTERVAL_MS = Number(__ENV.SEND_INTERVAL || 3) * 1000;
const DURATION_MS = Number(__ENV.DURATION || 120) * 1000;
// 접속 시작을 이 시간(초) 안에 고르게 흩는다. 0이면 모두 같은 순간에 접속한다 (방송 시작 직후 몰림)
const CONNECT_SPREAD_S = Number(__ENV.CONNECT_SPREAD || 0);
// 동시 접속이어도 연결이 몰리면 몇 초씩 늦는 사람이 있으므로, 보내기 시작 전에 이만큼은 기다린다
const JOIN_GRACE_S = Number(__ENV.JOIN_GRACE || 5);
const JOIN_WINDOW_S = Math.max(CONNECT_SPREAD_S, JOIN_GRACE_S);
// 보내기를 멈춘 뒤 나가기 전까지 기다리는 시간. 마지막에 보낸 메시지가 모두에게 도착하게 한다
const DRAIN_MS = 2000;

const echoLatency = new Trend('chat_echo_latency', true);
const received = new Counter('chat_received');
const sent = new Counter('chat_sent');
const chatErrors = new Counter('chat_error');
const stompConnected = new Rate('stomp_connected_rate');
const ready = new Counter('chat_ready');
const late = new Counter('chat_late');
const dropped = new Counter('chat_dropped');

export const options = {
  scenarios: {
    viewers: {
      executor: 'per-vu-iterations',
      vus: VIEWERS,
      iterations: 1,
      maxDuration: `${Math.ceil(DURATION_MS / 1000) + JOIN_WINDOW_S + 60}s`,
    },
  },
  thresholds: {
    stomp_connected_rate: ['rate>0.99'],
    chat_echo_latency: ['p(95)<500'],
    chat_error: ['count<10'],
    // 기대값 계산의 전제. 0이 아니면 chat_received 비교를 다시 계산해야 한다
    chat_late: ['count<1'],
    chat_dropped: ['count<1'],
  },
};

export function setup() {
  guardTarget();
}

const NUL = '\u0000';

function frame(command, headers, body) {
  const lines = [command];
  for (const key of Object.keys(headers)) {
    lines.push(`${key}:${headers[key]}`);
  }
  return `${lines.join('\n')}\n\n${body || ''}${NUL}`;
}

function parseFrames(data) {
  return String(data).split(NUL)
    .map((raw) => raw.replace(/^\n+/, ''))
    .filter((raw) => raw.length > 0)
    .map((raw) => {
      const split = raw.indexOf('\n\n');
      const head = (split < 0 ? raw : raw.slice(0, split)).split('\n');
      const headers = {};
      for (const line of head.slice(1)) {
        const i = line.indexOf(':');
        if (i > 0) headers[line.slice(0, i)] = line.slice(i + 1);
      }
      return { command: head[0], headers, body: split < 0 ? '' : raw.slice(split + 2) };
    });
}

// 채팅 필터가 숫자 묶음(전화번호·계좌번호)을 막으므로 메시지 표시는 영문자로만 만든다
function tagOf(n) {
  let s = '';
  do {
    s = String.fromCharCode(97 + (n % 26)) + s;
    n = Math.floor(n / 26);
  } while (n > 0);
  return s;
}

export default function () {
  const user = users[(__VU - 1) % users.length];
  const isSender = __VU <= SENDERS;
  const pending = {};
  let seq = 0;
  let connected = false;
  let sender = null;
  let closed = false;
  let starter = null;

  // 접속을 흩어도 모두가 같은 구간에 머물도록 시각을 맞춘다.
  // 접속 시작(CONNECT_SPREAD 안에서 흩음) → 테스트 시작 뒤 JOIN_WINDOW초에 모두 보내기 시작 → DURATION 동안 머묾 → 같은 시각에 멈추고 나감
  // 그 시각까지 연결이 끝났다는 보장은 없다. 늦은 연결은 chat_late, 도중 끊김은 chat_dropped로 세고,
  // 둘이 0일 때만 chat_received를 "chat_sent × chat_ready"와 비교한다. 구독 완료는 확인하지 못한다(위 머리말 참고)
  const delay = CONNECT_SPREAD_S > 0 ? Math.random() * CONNECT_SPREAD_S : 0;
  if (delay > 0) {
    sleep(delay);
  }
  const untilAllJoinedMs = (JOIN_WINDOW_S - delay) * 1000;
  const ws = new WebSocket(WS_URL);
  const joinedAt = Date.now();
  const sendStartAt = joinedAt + untilAllJoinedMs;
  let leaving = false;
  // 지금 연결이 살아 있는지. connected는 "한 번이라도 연결됐는지"라 끊긴 뒤에도 true로 남는다
  let open = false;
  // 지표가 0이어도 요약에 나오게 한다
  ready.add(0);
  late.add(0);
  dropped.add(0);

  // 보내기 시작 시각에 연결돼 있었는지 센다 (그 전에 끊긴 사람은 빼고, chat_dropped로 따로 센다)
  // 구독 완료는 확인하지 못한다: 내장 브로커는 SUBSCRIBE의 receipt에 RECEIPT를 돌려주지 않는다.
  // 대신 CONNECTED 직후 구독을 보내고, 보내기 시작 시각(테스트 시작 뒤 JOIN_WINDOW초, 모든 VU가 같은 시각)까지를 구독이 끝날 여유로 둔다.
  // 이 여유는 연결이 맺어진 때부터 세는 것이 아니므로, 그 시각 직전에 연결된 사람에게는 짧을 수 있다
  const readyCheck = setTimeout(() => {
    if (connected && open) ready.add(1);
  }, untilAllJoinedMs);

  ws.onopen = () => {
    ws.send(frame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '0,0',
      host: 'localhost',
      Authorization: `Bearer ${user.token}`,
    }));
  };

  ws.onmessage = (event) => {
    for (const f of parseFrames(event.data)) {
      if (f.command === 'CONNECTED') {
        connected = true;
        open = true;
        stompConnected.add(true);
        if (Date.now() >= sendStartAt) {
          late.add(1);
        }
        ws.send(frame('SUBSCRIBE', { id: 'chat', destination: `/topic/live-streams/${LIVE_STREAM_ID}/chat` }));
        ws.send(frame('SUBSCRIBE', { id: 'errors', destination: '/user/queue/errors' }));
        if (isSender) {
          // 모두 들어온 뒤에 보내기 시작하고, 보내는 사람들이 같은 순간에 몰리지 않게 시작을 흩는다
          // 접속이 늦어 보내기를 멈출 시각이 먼저 지났으면 시작하지 않는다
          starter = setTimeout(() => {
            if (closed) return;
            sender = setInterval(() => {
              if (!closed) sendChat();
            }, SEND_INTERVAL_MS);
          }, Math.max(0, sendStartAt - Date.now()) + Math.random() * SEND_INTERVAL_MS);
        }
      } else if (f.command === 'MESSAGE') {
        if (f.headers.subscription === 'errors') {
          chatErrors.add(1);
          continue;
        }
        received.add(1);
        if (isSender) {
          const match = /\[([a-z]+)\]$/.exec(safeContent(f.body));
          if (match && pending[match[1]]) {
            echoLatency.add(Date.now() - pending[match[1]]);
            delete pending[match[1]];
          }
        }
      } else if (f.command === 'ERROR') {
        chatErrors.add(1, { stage: 'stomp' });
        console.warn(`STOMP ERROR (VU ${__VU}): ${f.headers.message || f.body}`);
      }
    }
  };

  ws.onclose = () => {
    open = false;
    // 정해진 때보다 먼저 끊겼으면 센다 (연결에 성공했던 사람만)
    if (connected && !leaving) {
      dropped.add(1);
    }
  };

  ws.onerror = (e) => {
    console.warn(`WebSocket 오류 (VU ${__VU}): ${e.error}`);
  };

  function sendChat() {
    // VU 번호와 순번으로 메시지마다 다른 표시를 붙인다 (같은 말 반복 제한을 피하고, 돌아온 메시지를 찾는다)
    const tag = tagOf(__VU * 100000 + seq++);
    pending[tag] = Date.now();
    sent.add(1);
    ws.send(frame('SEND', {
      destination: `/app/live-streams/${LIVE_STREAM_ID}/chat`,
      'content-type': 'application/json',
    }, JSON.stringify({ content: `부하 테스트 메시지 [${tag}]` })));
  }

  // 모두가 같은 시각에 보내기를 멈추고, 이미 보낸 메시지가 도착할 시간(DRAIN_MS)을 둔 뒤 나간다
  setTimeout(() => {
    closed = true;
    clearTimeout(readyCheck);
    if (starter) clearTimeout(starter);
    if (sender) clearInterval(sender);
    setTimeout(() => {
      if (!connected) {
        stompConnected.add(false);
      }
      check(connected, { 'STOMP 연결됨': (c) => c });
      leaving = true;
      ws.close();
    }, DRAIN_MS);
  }, untilAllJoinedMs + DURATION_MS);
}

function safeContent(body) {
  try {
    return JSON.parse(body).content || '';
  } catch (e) {
    return '';
  }
}
