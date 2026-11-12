// 시나리오 F. 로그인 IP 제한 확인: 같은 IP에서 로그인을 35번 보내 31번째부터 막히는지 본다. (IP당 5분에 30번)
// 실행: k6 run login-limit.js
// ⚠ 끝나면 내 IP는 5분 동안 로그인이 막힌다. 테스트 회원(seed.sql)이 있어야 한다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './common.js';

const TOTAL = Number(__ENV.TOTAL || 35);

export const options = { vus: 1, iterations: 1 };

export default function () {
  let ok = 0;
  let limited = 0;
  let firstLimitedAt = 0;
  for (let i = 1; i <= TOTAL; i++) {
    const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ email: 'loadtest001@ddang.local', password: 'Loadtest1!' }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'login' },
      responseCallback: http.expectedStatuses(200, 429),
    });
    if (res.status === 200) ok++;
    if (res.status === 429) {
      limited++;
      if (!firstLimitedAt) firstLimitedAt = i;
    }
  }
  console.log(`로그인 ${TOTAL}번: 성공 ${ok}, 막힘 ${limited}, 처음 막힌 순번 ${firstLimitedAt || '없음'}`);
  check(null, {
    '30번까지는 로그인된다': () => ok === 30,
    '31번째부터 막힌다': () => firstLimitedAt === 31,
  });
}
