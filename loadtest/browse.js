// 시나리오 B. 구경: VUS명(기본 100)이 경매 목록(정렬·검색)·상세·입찰 기록·광고를 계속 본다. (로그인 없이)
// 대량 경매로: k6 run -e BULK_AUCTIONS=10000 -e VUS=300 browse.js
// 경매 하나로: k6 run -e AUCTION_ID=경매번호 browse.js
// dev 서버: k6 run -e BASE_URL=https://dev서버API -e VUS=300 -e HOLD=3m30s browse.js (경매는 목록에서 골라 본다)
import { browseOnce, stagesFor, VUS } from './common.js';

export const options = {
  scenarios: {
    viewers: { executor: 'ramping-vus', startVUs: 0, stages: stagesFor(VUS), gracefulRampDown: '10s' },
  },
  // 이름마다 기준을 걸어야 요약·결과 파일에 이름별 숫자가 남는다
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:auction_list_LATEST}': ['p(95)<500'],
    'http_req_duration{name:auction_list_ENDING_SOON}': ['p(95)<500'],
    'http_req_duration{name:auction_list_PRICE_ASC}': ['p(95)<500'],
    'http_req_duration{name:auction_list_PRICE_DESC}': ['p(95)<500'],
    'http_req_duration{name:auction_list_POPULAR}': ['p(95)<500'],
    'http_req_duration{name:auction_search}': ['p(95)<500'],
    'http_req_duration{name:auction_popular}': ['p(95)<500'],
    'http_req_duration{name:auction_detail}': ['p(95)<300'],
    'http_req_duration{name:bid_history}': ['p(95)<500'],
    'http_req_duration{name:ads}': ['p(95)<500'],
  },
};

export default function () {
  browseOnce();
}
