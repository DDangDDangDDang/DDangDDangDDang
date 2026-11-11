// 시나리오 E. 마감 몰림: 마감 시각이 같은 경매 여러 개(seed-close.sql)에 입찰자들이 마감 직전까지 입찰한다.
// 테스트가 끝나 입찰이 멈추면 30초 뒤 경매들이 한꺼번에 마감되고, 마감 처리(낙찰·주문·정산·알림)가 몰린다.
// 실행: (seed-close.sql 직후) k6 run -e CLOSE_COUNT=10 -e VUS=30 -e HOLD=3m close-rush.js
// 결과는 테스트가 끝나고 1~2분 뒤 check-close.sql로 본다.
import { bidOnce, prepareTokens, stagesFor, VUS } from './common.js';

const CLOSE_FIRST_ID = 9100001;
const CLOSE_COUNT = Number(__ENV.CLOSE_COUNT || 10);

export const options = {
  scenarios: {
    bidders: { executor: 'ramping-vus', startVUs: 0, stages: stagesFor(VUS), gracefulRampDown: '10s' },
  },
  thresholds: {
    'http_req_duration{name:bid}': ['p(95)<1000'],
    bid_unexpected: ['count<10'],
  },
};

export function setup() {
  return prepareTokens(VUS, false); // 경매 번호는 9100001부터 CLOSE_COUNT개를 쓴다
}

export default function (data) {
  const auctionId = CLOSE_FIRST_ID + Math.floor(Math.random() * CLOSE_COUNT);
  bidOnce(data, auctionId);
}
