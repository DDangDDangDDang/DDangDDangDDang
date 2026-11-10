// 입찰 부하: 여러 회원이 동시에 입찰한다. 기본은 한 경매에 몰리는 경우(MODE=hot), MODE=spread면 시드 경매에 고르게 나눈다.
// 실행: k6 run -e BASE_URL=http://localhost:8080 load-test/scenarios/bid.js
// 초당 입찰 시도 수는 RATE(기본 50)로, 유지 시간은 DURATION(기본 2m)으로 바꾼다.
//
// 보는 것
// - 5xx·타임아웃이 없는지 (http_req_failed). 400/409는 경쟁에서 진 정상 응답이라 실패로 세지 않는다.
// - 입찰 응답 시간 (http_req_duration{name:입찰})
// - 결과 코드별 개수 (bid_result{code:...}): AUCTION_018(BID_CONFLICT, 재시도 5번 실패)이 많으면 락 경합이 한계
// - 끝난 뒤 정합성: 모든 입찰을 최소 금액으로 넣으므로 현재가 = 시작가 + 단위 × (입찰 수 - 1) 이어야 한다
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { BASE_URL, authHeaders, dataOf, errorCodeOf, nextBidAmount, guardTarget } from '../lib/common.js';

const seed = JSON.parse(open(__ENV.SEED || '../data/seed.json'));
const users = new SharedArray('users', () => seed.users);
const auctionIds = seed.auctionIds;
const MODE = __ENV.MODE || 'hot';
const RATE = Number(__ENV.RATE || 50);

const bidResult = new Counter('bid_result');
const bidAccepted = new Rate('bid_accepted');

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 400, 409));

export const options = {
  scenarios: {
    bid: {
      executor: 'ramping-arrival-rate',
      startRate: Math.max(1, Math.floor(RATE / 5)),
      timeUnit: '1s',
      preAllocatedVUs: Math.min(users.length, RATE * 2),
      maxVUs: users.length,
      stages: [
        { duration: __ENV.RAMP || '30s', target: RATE },
        { duration: __ENV.DURATION || '2m', target: RATE },
        { duration: __ENV.RAMP_DOWN || '15s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:입찰}': ['p(95)<1000'],
    'bid_result{code:AUCTION_018}': ['count<50'],
    // 아래는 결과 요약에 코드별 개수를 보이게 하려는 항목 (항상 통과)
    'bid_result{code:OK}': ['count>=0'],
    'bid_result{code:AUCTION_015}': ['count>=0'], // 이미 최고 입찰자
    'bid_result{code:AUCTION_016}': ['count>=0'], // 최소 입찰가보다 낮음 (경쟁에서 짐)
  },
};

export function setup() {
  guardTarget();
  const res = http.get(`${BASE_URL}/api/v1/auctions/${auctionIds[0]}`, { tags: { name: '시드 확인' } });
  if (res.status !== 200) {
    throw new Error(`시드 경매(${auctionIds[0]})를 읽지 못했습니다: HTTP ${res.status}. 서버를 loadtest 프로필로 켰는지 확인하세요.`);
  }
}

export default function () {
  // VU마다 다른 회원을 쓴다. (같은 회원이 동시에 입찰하면 '이미 최고 입찰자'만 늘어난다)
  const user = users[(__VU - 1) % users.length];
  const auctionId = MODE === 'spread'
    ? auctionIds[Math.floor(Math.random() * auctionIds.length)]
    : auctionIds[0];

  const detail = http.get(`${BASE_URL}/api/v1/auctions/${auctionId}`, { tags: { name: '경매 상세' } });
  const auction = dataOf(detail);
  if (!auction) {
    bidResult.add(1, { code: errorCodeOf(detail) });
    return;
  }
  if (auction.status !== 'ONGOING') {
    bidResult.add(1, { code: `STATUS_${auction.status}` });
    sleep(1);
    return;
  }

  const res = http.post(
    `${BASE_URL}/api/v1/auctions/${auctionId}/bids`,
    JSON.stringify({ amount: nextBidAmount(auction) }),
    Object.assign(authHeaders(user.token), { tags: { name: '입찰' } }),
  );
  const accepted = res.status === 201;
  bidAccepted.add(accepted);
  bidResult.add(1, { code: accepted ? 'OK' : errorCodeOf(res) });
  check(res, { '입찰 5xx 없음': (r) => r.status < 500 });
}

export function teardown() {
  for (const id of auctionIds) {
    const auction = dataOf(http.get(`${BASE_URL}/api/v1/auctions/${id}`, { tags: { name: '정합성 확인' } }));
    if (!auction || auction.bidCount === 0) {
      continue;
    }
    const expected = auction.startPrice + auction.bidUnit * (auction.bidCount - 1);
    const consistent = auction.currentPrice === expected;
    console.log(`경매 ${id}: 입찰 ${auction.bidCount}건, 현재가 ${auction.currentPrice} (기대값 ${expected}) → ${consistent ? '정합성 OK' : '불일치!'}`);
    check(auction, { [`경매 ${id} 현재가 정합성`]: () => consistent });
  }
}
