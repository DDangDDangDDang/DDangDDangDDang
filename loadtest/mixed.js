// 시나리오 C. 섞어서: 입찰자 BIDDERS명(기본 20) + 구경꾼 VIEWERS명(기본 80). 입찰이 몰릴 때 조회가 느려지는지 본다.
// 실행: k6 run -e AUCTION_ID=경매번호 -e BULK_AUCTIONS=10000 -e BIDDERS=100 -e VIEWERS=400 mixed.js
import { bidOnce, browseOnce, prepareTokens, stagesFor } from './common.js';

const BIDDERS = Number(__ENV.BIDDERS || 20);
const VIEWERS = Number(__ENV.VIEWERS || 80);


export const options = {
  scenarios: {
    bidders: { executor: 'ramping-vus', startVUs: 0, stages: stagesFor(BIDDERS), gracefulRampDown: '10s', exec: 'bidders' },
    viewers: { executor: 'ramping-vus', startVUs: 0, stages: stagesFor(VIEWERS), gracefulRampDown: '10s', exec: 'viewers' },
  },
  thresholds: {
    'http_req_duration{name:bid}': ['p(95)<1000'],
    'http_req_duration{name:auction_list_LATEST}': ['p(95)<500'],
    bid_unexpected: ['count<10'],
  },
};

export function setup() {
  return prepareTokens(BIDDERS + VIEWERS); // VU 번호는 구경꾼까지 통틀어 매겨진다
}

export function bidders(data) {
  bidOnce(data);
}

export function viewers() {
  browseOnce();
}
