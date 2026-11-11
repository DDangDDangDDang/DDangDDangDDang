// 시나리오 A. 입찰 경쟁: VUS명(기본 100)이 같은 경매에 계속 입찰한다.
// 실행: k6 run -e AUCTION_ID=경매번호 -e VUS=300 bid-race.js   (VUS는 seed.sql의 입찰자 수 이하)
import { bidOnce, prepareTokens, stagesFor, VUS } from './common.js';

export const options = {
  scenarios: {
    bidders: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: stagesFor(VUS), // 30초 늘리기 → HOLD(기본 2분) 유지 → 30초 줄이기
      gracefulRampDown: '10s',
    },
  },
  thresholds: {
    'http_req_duration{name:bid}': ['p(95)<1000'], // 입찰 응답 95%가 1초 안
    bid_unexpected: ['count<10'],
  },
};

export function setup() {
  return prepareTokens();
}

export default function (data) {
  bidOnce(data);
}
