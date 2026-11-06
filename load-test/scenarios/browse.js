// 조회 부하: 비로그인 방문자가 홈 → 경매 목록 → 인기 경매 → 경매 상세 → 입찰 내역을 둘러본다.
// 실행: k6 run -e BASE_URL=http://localhost:8080 load-test/scenarios/browse.js
// 단계별 인원은 VUS(최대 동시 사용자, 기본 100), DURATION(최대 인원 유지 시간, 기본 3m)으로 바꾼다.
import { sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import { getJson, dataOf, guardTarget } from '../lib/common.js';

const seed = new SharedArray('seed', () => [JSON.parse(open(__ENV.SEED || '../data/seed.json'))]);

const VUS = Number(__ENV.VUS || 100);

export const options = {
  scenarios: {
    browse: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: VUS },
        { duration: __ENV.DURATION || '3m', target: VUS },
        { duration: __ENV.RAMP_DOWN || '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1500'],
    'http_req_duration{name:경매 목록}': ['p(95)<500'],
    'http_req_duration{name:경매 상세}': ['p(95)<300'],
  },
};

export function setup() {
  guardTarget();
}

export default function () {
  const auctionIds = seed[0].auctionIds;

  group('홈', () => {
    getJson('/api/v1/live-streams/home', null, '방송 홈');
    getJson('/api/v1/categories', null, '카테고리');
  });
  sleep(1 + Math.random());

  let listed = [];
  group('경매 목록', () => {
    const res = getJson('/api/v1/auctions?page=0&size=20', null, '경매 목록');
    const data = dataOf(res);
    listed = (data && data.content) || [];
    getJson('/api/v1/auctions/popular', null, '인기 경매');
  });
  sleep(1 + Math.random());

  // 목록에서 하나 고르되, 목록이 비었으면 시드 경매를 본다
  const picked = listed.length > 0 && Math.random() < 0.5
    ? listed[Math.floor(Math.random() * listed.length)].auctionId
    : auctionIds[Math.floor(Math.random() * auctionIds.length)];

  group('경매 상세', () => {
    getJson(`/api/v1/auctions/${picked}`, null, '경매 상세');
    getJson(`/api/v1/auctions/${picked}/bids`, null, '입찰 내역');
  });
  sleep(2 + Math.random() * 2);
}
