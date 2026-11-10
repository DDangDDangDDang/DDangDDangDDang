// 시나리오 공통 도구. 시드 파일(seed.json)은 각 시나리오의 init 단계에서 open()으로 읽어 넘긴다.
// (k6의 open()은 init 단계에서만 쓸 수 있고, 상대 경로는 open을 부른 파일 기준이다)
import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
export const WS_URL = __ENV.WS_URL || BASE_URL.replace(/^http/, 'ws') + '/ws';

/** 운영 주소로 잘못 돌리는 일을 막는다. WS_URL은 따로 지정할 수 있어 함께 본다. 꼭 필요하면 ALLOW_PROD=true */
export function guardTarget() {
  if (__ENV.ALLOW_PROD === 'true') {
    return;
  }
  for (const url of [BASE_URL, WS_URL]) {
    if (/ddangddangddang\.com/i.test(url) && !/(^|\/\/)dev\./i.test(url)) {
      throw new Error(`운영 주소(${url})에는 부하 테스트를 돌리지 않습니다. 꼭 필요하면 ALLOW_PROD=true`);
    }
  }
}

export function authHeaders(token) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
}

/** ApiResponse({success, data, error})에서 data를 꺼낸다. 실패하면 null */
export function dataOf(res) {
  try {
    const body = res.json();
    return body && body.success ? body.data : null;
  } catch (e) {
    return null;
  }
}

/** 실패 응답의 오류 코드 (AUCTION_018 등). 본문이 JSON이 아니면 'HTTP_{status}' */
export function errorCodeOf(res) {
  try {
    const body = res.json();
    return (body && body.error && body.error.code) || `HTTP_${res.status}`;
  } catch (e) {
    return `HTTP_${res.status}`;
  }
}

export function getJson(path, params, name) {
  const res = http.get(`${BASE_URL}${path}`, Object.assign({ tags: { name: name || path } }, params || {}));
  check(res, { [`${name || path} 200`]: (r) => r.status === 200 });
  return res;
}

/** 다음에 받을 수 있는 최소 입찰가 (Auction.minimumBidAmount와 같은 규칙) */
export function nextBidAmount(auction) {
  return auction.bidCount === 0 ? auction.startPrice : auction.currentPrice + auction.bidUnit;
}
