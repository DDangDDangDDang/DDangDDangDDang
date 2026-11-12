// 부하 테스트 공통: 서버 주소, 테스트 회원 토큰, 입찰·구경 한 번의 동작.
import crypto from 'k6/crypto';
import encoding from 'k6/encoding';
import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import ws from 'k6/ws';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const AUCTION_ID = __ENV.AUCTION_ID;
// 입찰자 수. seed.sql의 @bidders보다 크면 안 된다 (토큰은 있어도 DB에 회원이 없다)
export const VUS = Number(__ENV.VUS || 100);
export const USER_COUNT = Number(__ENV.USER_COUNT || VUS);
// 대량 경매(seed-auctions.sql) 개수. 0이면 구경할 때 AUCTION_ID 하나만 본다
export const BULK_AUCTIONS = Number(__ENV.BULK_AUCTIONS || 0);
const BULK_FIRST_ID = 9000001;
// 최대 인원을 유지하는 시간 (기본 2m). 앞뒤 늘리기·줄이기 30초씩이 더해진다. 예) HOLD=3m30s → 총 4분 30초
export const HOLD = __ENV.HOLD || '2m';

/** 30초 늘리기 → HOLD 유지 → 30초 줄이기 */
export const stagesFor = (target) => [
  { duration: '30s', target },
  { duration: HOLD, target },
  { duration: '30s', target: 0 },
];
const FIRST_USER_ID = 900001;
// 로컬 기본 JWT 비밀값 (application.yml의 기본값). IntelliJ에 JWT_SECRET을 따로 넣었다면 -e JWT_SECRET=같은값
const JWT_SECRET = __ENV.JWT_SECRET || 'bG9jYWwtZGV2ZWxvcG1lbnQtand0LXNlY3JldC1rZXktZGRhbmctZGRhbmctZGRhbmc=';

/**
 * 테스트 회원 토큰을 직접 만든다. 로그인 API는 IP당 5분에 30번으로 막혀 있어서
 * 100명이 한 번에 로그인할 수 없다. 서버가 만드는 토큰과 같은 형식으로 서명한다.
 * (서버의 로컬 비밀값은 400비트라 jjwt가 HS384로 서명한다. 같은 알고리즘을 쓴다)
 */
export function tokenFor(index) {
  const userId = FIRST_USER_ID + index;
  const no = String(index + 1).padStart(3, '0');
  const now = Math.floor(Date.now() / 1000);
  const part = (value) => encoding.b64encode(JSON.stringify(value), 'rawurl');
  const header = part({ alg: 'HS384' });
  const payload = part({
    sub: String(userId),
    jti: `loadtest-${userId}-${now}`,
    type: 'ACCESS',
    email: `loadtest${no}@ddang.local`,
    nickname: `lt${no}`,
    role: 'USER',
    iat: now,
    exp: now + 60 * 60,
  });
  const signature = crypto.hmac('sha384', encoding.b64decode(JWT_SECRET), `${header}.${payload}`, 'base64rawurl');
  return `${header}.${payload}.${signature}`;
}

/** 테스트 시작 전 확인: 경매 번호가 있고, 만든 토큰이 서버에서 통하는지 */
/** 번호 값 확인: 비었거나 숫자가 아니면(예: README의 '번호'를 그대로 넣음) 테스트를 시작하지 않는다 */
export function requireNumber(name, value) {
  if (!value || !/^\d+$/.test(String(value))) {
    throw new Error(`${name}에 실제 숫자를 넣어 주세요. 지금 값: '${value || ''}' (예: -e ${name}=9)`);
  }
}

export function prepareTokens(count = USER_COUNT, needAuctionId = true) {
  if (needAuctionId) requireNumber('AUCTION_ID', AUCTION_ID);
  const tokens = Array.from({ length: count }, (_, i) => tokenFor(i));
  const res = http.get(`${BASE_URL}/api/v1/points/wallet`, { headers: { Authorization: `Bearer ${tokens[0]}` } });
  if (res.status !== 200) {
    throw new Error(`테스트 회원 토큰이 통하지 않아요 (${res.status}). seed.sql을 실행했는지, JWT_SECRET이 같은지 확인해 주세요. ${res.body}`);
  }
  return { tokens };
}

// 입찰 결과 개수 (k6 결과 맨 아래에 나온다)
export const bidSuccess = new Counter('bid_success'); // 입찰 성공
export const bidTooLow = new Counter('bid_too_low'); // 보는 사이 현재가가 올라 거절 (AUCTION_016, 정상)
export const bidAlreadyTop = new Counter('bid_already_top'); // 이미 최고 입찰자라 거절 (AUCTION_015, 정상)
export const bidConflict = new Counter('bid_conflict'); // 동시 입찰이 몰려 5번 재시도 후 실패 (AUCTION_018)
export const bidUnexpected = new Counter('bid_unexpected'); // 그 밖의 오류 (원인 확인 필요)

function errorCode(res) {
  try {
    return res.json('error.code');
  } catch {
    return undefined;
  }
}

/**
 * 입찰자 한 번: 경매를 보고 → 최소 입찰가에서 0~2단위 더 얹어 입찰 → 1~2초 쉬기.
 * 사람처럼 "본 값으로 입찰"하므로, 그 사이 다른 입찰이 들어오면 bid_too_low로 거절된다. 다음 반복에서 새 값으로 다시 시도한다.
 */
export function bidOnce(data, auctionId = AUCTION_ID) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const detail = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, { tags: { name: 'auction_detail' } });
  if (detail.status !== 200) {
    bidUnexpected.add(1);
    if (__ITER < 1) console.warn(`경매 ${auctionId} 조회 실패 ${detail.status} ${String(detail.body).slice(0, 200)}`);
    sleep(1);
    return;
  }
  const auction = detail.json('data');
  if (auction.status !== 'ONGOING') {
    sleep(5); // 경매가 시작 전이거나 끝남
    return 'CLOSED';
  }
  const unit = auction.bidUnit;
  const minimum = auction.bidCount === 0 ? auction.startPrice : auction.currentPrice + unit;
  const amount = minimum + unit * Math.floor(Math.random() * 3);

  const res = http.post(`${BASE_URL}/api/v1/auctions/${auctionId}/bids`, JSON.stringify({ amount }), {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    tags: { name: 'bid' },
    responseCallback: http.expectedStatuses(201, 400, 409), // 거절도 정상 동작이라 실패율에서 뺀다
  });
  const code = res.status === 201 ? 'OK' : errorCode(res);
  if (code === 'OK') bidSuccess.add(1);
  else if (code === 'AUCTION_016') bidTooLow.add(1);
  else if (code === 'AUCTION_015') bidAlreadyTop.add(1);
  else if (code === 'AUCTION_018') bidConflict.add(1);
  else {
    bidUnexpected.add(1);
    if (__ITER < 3) console.warn(`예상 못 한 입찰 응답 ${res.status} ${res.body}`);
  }
  sleep(1 + Math.random());
}

const SORTS = ['LATEST', 'ENDING_SOON', 'PRICE_ASC', 'PRICE_DESC', 'POPULAR'];
const KEYWORDS = ['카메라', '시계', '운동화', '가방', '노트북'];
const pick = (list) => list[Math.floor(Math.random() * list.length)];

/**
 * 구경할 경매. 순서대로: 대량 경매 번호(BULK_AUCTIONS) → 방금 본 목록의 경매 중 아무거나 → AUCTION_ID.
 * dev처럼 경매 번호를 모르는 서버에서도 목록에서 골라 상세를 본다.
 */
function anyAuctionId(listResponse) {
  if (BULK_AUCTIONS > 0) return BULK_FIRST_ID + Math.floor(Math.random() * BULK_AUCTIONS);
  try {
    const content = listResponse.json('data.content');
    if (Array.isArray(content) && content.length > 0) return pick(content).auctionId;
  } catch {
    // 목록 응답이 실패했으면 아래로
  }
  return AUCTION_ID;
}

/**
 * 구경꾼 한 번: 목록(정렬·페이지 무작위, 가끔 검색) → 인기 → 상세 → 입찰 기록 → 광고, 1~3초 쉬기 (로그인 없이)
 * 정렬마다 쿼리가 달라 태그를 나눈다 (auction_list_LATEST 등). 검색은 auction_search.
 */
export function browseOnce() {
  const sort = pick(SORTS);
  const page = Math.floor(Math.random() * 5);
  const list = http.get(`${BASE_URL}/api/v1/auctions?sortType=${sort}&page=${page}&size=30`, { tags: { name: `auction_list_${sort}` } });
  if (Math.random() < 0.2) {
    http.get(`${BASE_URL}/api/v1/auctions?productName=${encodeURIComponent(pick(KEYWORDS))}&size=30`, { tags: { name: 'auction_search' } });
  }
  http.get(`${BASE_URL}/api/v1/auctions/popular?size=10`, { tags: { name: 'auction_popular' } });
  const auctionId = anyAuctionId(list);
  if (auctionId) {
    http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, { tags: { name: 'auction_detail' } });
    http.get(`${BASE_URL}/api/v1/auctions/${auctionId}/bids?size=10`, { tags: { name: 'bid_history' } });
  }
  http.get(`${BASE_URL}/api/v1/ads?placement=SIDEBAR`, { tags: { name: 'ads' } });
  sleep(1 + Math.random() * 2);
}

// ---------------------------------------------------------------- 실시간 (WebSocket)

export const wsConnected = new Rate('ws_connected'); // 연결·구독 성공 비율
export const wsMessages = new Counter('ws_messages'); // 받은 실시간 메시지 수 (모든 구경꾼 합)
export const wsErrors = new Counter('ws_errors'); // STOMP ERROR 프레임·연결 오류
export const wsConnectTime = new Trend('ws_connect_time', true); // 연결 요청부터 구독까지 걸린 시간

const WS_URL = `${BASE_URL.replace(/^http/, 'ws')}/ws`;
const NUL = String.fromCharCode(0);
const frame = (command, headers) => `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${NUL}`;

/**
 * 구경꾼 한 명: 로그인 없이(손님) WebSocket으로 연결해 경매 토픽을 구독하고, seconds초 동안 실시간 메시지를 받는다.
 * 브라우저의 경매 상세 화면과 같다. (STOMP: CONNECT → SUBSCRIBE /topic/auctions/{id})
 */
export function watchAuction(auctionId, seconds) {
  const startedAt = Date.now();
  let subscribed = false;
  const res = ws.connect(WS_URL, { tags: { name: 'ws_auction' } }, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', { 'accept-version': '1.2', host: 'ddang', 'heart-beat': '0,0' }));
    });
    socket.on('message', (data) => {
      if (data.startsWith('CONNECTED')) {
        socket.send(frame('SUBSCRIBE', { id: 'sub-0', destination: `/topic/auctions/${auctionId}` }));
        subscribed = true;
        wsConnectTime.add(Date.now() - startedAt);
        wsConnected.add(1);
      } else if (data.startsWith('MESSAGE')) {
        wsMessages.add(1);
      } else if (data.startsWith('ERROR')) {
        wsErrors.add(1);
        if (__ITER < 1) console.warn(`STOMP 오류: ${data.slice(0, 200)}`);
      }
    });
    socket.on('error', () => wsErrors.add(1));
    socket.setTimeout(() => socket.close(), seconds * 1000);
  });
  if (!res || res.status !== 101 || !subscribed) {
    wsConnected.add(0);
    if (__ITER < 1) console.warn(`WebSocket 연결 실패 status=${res && res.status}`);
    sleep(1);
  }
}

// ---------------------------------------------------------------- 방송 (입장·채팅)

export const chatSent = new Counter('chat_sent'); // 보낸 채팅 수
export const chatReceived = new Counter('chat_received'); // 받은 채팅 수 (모든 시청자 합)
export const chatErrors = new Counter('chat_errors'); // 서버가 보낸 채팅 오류 (도배 제한, 금칙어 등)
export const chatEchoTime = new Trend('chat_echo_time', true); // 내가 보낸 채팅이 나에게 다시 도착하기까지 (= 다른 시청자에게 퍼지는 시간)

const CHAT_WORDS_A = ['와', '오', '헐', '대박', '진짜', '우와', '좋아요', '멋져요', '최고', '굿'];
const CHAT_WORDS_B = ['이거', '가격', '물건', '상태', '색감', '구성', '포장', '디자인', '사이즈', '느낌'];
const CHAT_WORDS_C = ['좋네요', '괜찮다', '탐나요', '갖고싶다', '예뻐요', '살까', '고민돼요', '마음에들어요', '신기해요', '궁금해요'];

/**
 * 채팅 내용. 서버 금칙어 검사(숫자 연속·연락처·링크)에 걸리지 않게 숫자 없이 한글로만 만들고,
 * 같은 내용 반복 제한(30초에 2번)에 걸리지 않게 끝에 메시지마다 다른 한글 두 글자를 붙인다. (보낸 채팅을 다시 찾는 표시로도 쓴다)
 */
function chatContent(seq) {
  const pick3 = (list) => list[Math.floor(Math.random() * list.length)];
  const code = String.fromCharCode(0xac00 + ((__VU * 131 + seq * 7) % 11172), 0xac00 + ((seq * 37 + __VU) % 11172));
  return { text: `${pick3(CHAT_WORDS_A)} ${pick3(CHAT_WORDS_B)} ${pick3(CHAT_WORDS_C)} ${code}`, code };
}

/** 방송 입장: 시청 토큰 발급 + 방송 정보 + 방송에 올라간 경매 (영상(LiveKit)에는 연결하지 않는다) */
export function enterLive(liveId, token) {
  const headers = { Authorization: `Bearer ${token}` };
  const join = http.post(`${BASE_URL}/api/v1/live-streams/${liveId}/join`, null, { headers, tags: { name: 'live_join' } });
  const detail = http.get(`${BASE_URL}/api/v1/live-streams/${liveId}`, { tags: { name: 'live_detail' } });
  http.get(`${BASE_URL}/api/v1/lives/${liveId}/auctions`, { tags: { name: 'live_auctions' } });
  if (join.status !== 200 && __ITER < 1) {
    console.warn(`방송 입장 실패 ${join.status} ${join.body} / 방송 정보 ${detail.status} ${String(detail.body).slice(0, 200)}`);
  }
  return join.status === 200;
}

/**
 * 시청자 한 명이 seconds초 동안 방송을 보며 채팅 토픽 등을 구독한다. (로그인한 회원으로 연결)
 * chatEverySeconds를 주면 그 간격으로 채팅도 보낸다. 서버 도배 제한(5초에 5개)을 넘지 않게 2초 이상으로 쓴다.
 */
export function watchLive(liveId, token, seconds, chatEverySeconds = 0) {
  const startedAt = Date.now();
  const sentAt = {};
  let subscribed = false;
  let seq = 0;
  const res = ws.connect(WS_URL, { tags: { name: 'ws_live' } }, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', { 'accept-version': '1.2', host: 'ddang', 'heart-beat': '0,0', Authorization: `Bearer ${token}` }));
    });
    socket.on('message', (data) => {
      if (data.startsWith('CONNECTED')) {
        socket.send(frame('SUBSCRIBE', { id: 'sub-chat', destination: `/topic/live-streams/${liveId}/chat` }));
        socket.send(frame('SUBSCRIBE', { id: 'sub-live', destination: `/topic/live-streams/${liveId}` }));
        socket.send(frame('SUBSCRIBE', { id: 'sub-auctions', destination: `/topic/live-streams/${liveId}/auctions` }));
        socket.send(frame('SUBSCRIBE', { id: 'sub-errors', destination: '/user/queue/errors' }));
        subscribed = true;
        wsConnectTime.add(Date.now() - startedAt);
        wsConnected.add(1);
        if (chatEverySeconds > 0) {
          socket.setInterval(() => {
            const { text, code } = chatContent(seq++);
            sentAt[code] = Date.now();
            socket.send(
              `SEND\ndestination:/app/live-streams/${liveId}/chat\ncontent-type:application/json\n\n${JSON.stringify({ content: text })}${NUL}`,
            );
            chatSent.add(1);
          }, chatEverySeconds * 1000);
        }
      } else if (data.startsWith('MESSAGE')) {
        if (data.includes('subscription:sub-errors')) {
          chatErrors.add(1);
          if (__ITER < 1) console.warn(`채팅 오류: ${data.slice(data.indexOf('\n\n') + 2, data.indexOf('\n\n') + 200)}`);
        } else if (data.includes('subscription:sub-chat')) {
          chatReceived.add(1);
          for (const code of Object.keys(sentAt)) {
            if (data.includes(code)) {
              chatEchoTime.add(Date.now() - sentAt[code]);
              delete sentAt[code];
              break;
            }
          }
        }
      } else if (data.startsWith('ERROR')) {
        wsErrors.add(1);
        if (__ITER < 1) console.warn(`STOMP 오류: ${data.slice(0, 200)}`);
      }
    });
    socket.on('error', () => wsErrors.add(1));
    socket.setTimeout(() => socket.close(), seconds * 1000);
  });
  if (!res || res.status !== 101 || !subscribed) {
    wsConnected.add(0);
    if (__ITER < 1) console.warn(`WebSocket 연결 실패 status=${res && res.status}`);
    sleep(1);
  }
}
