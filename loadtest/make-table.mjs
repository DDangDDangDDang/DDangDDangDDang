// k6 결과 파일(--summary-export로 저장한 json) 여러 개를 단계별 비교 표로 만든다. (Node로 실행, 설치 필요 없음)
// 사용: node make-table.mjs results/showtime-100.json results/showtime-200.json results/showtime-300.json
// 결과는 화면에 마크다운 표로 나오고, results/table.md에도 저장된다. 노션에 그대로 붙여 넣으면 표가 된다.
import { readFileSync, writeFileSync } from 'node:fs';

const files = process.argv.slice(2);
if (files.length === 0) {
  console.error('결과 파일을 넣어 주세요. 예) node make-table.mjs results/showtime-100.json results/showtime-200.json');
  process.exit(1);
}

const runs = files.map((file) => JSON.parse(readFileSync(file, 'utf8')).metrics);
const get = (m, name, key) => (m[name] && m[name][key] !== undefined ? m[name][key] : null);
const ms = (x) => (x === null ? '-' : x >= 1000 ? `${(x / 1000).toFixed(2)}s` : `${x.toFixed(1)}ms`);
const num = (x) => (x === null ? '0' : Math.round(x).toLocaleString('ko-KR'));
const pct = (x) => (x === null ? '-' : `${(x * 100).toFixed(2)}%`);

const rows = [
  ['동시 사용자 (최대)', (m) => `${num(get(m, 'vus_max', 'value') ?? get(m, 'vus_max', 'max'))}명`],
  ['전체 응답 평균', (m) => ms(get(m, 'http_req_duration', 'avg'))],
  ['전체 응답 p95', (m) => ms(get(m, 'http_req_duration', 'p(95)'))],
  ['초당 HTTP 요청', (m) => (get(m, 'http_reqs', 'rate') === null ? '-' : get(m, 'http_reqs', 'rate').toFixed(1))],
  ['방송 입장 p95', (m) => ms(get(m, 'http_req_duration{name:live_join}', 'p(95)'))],
  ['방송 정보 p95', (m) => ms(get(m, 'http_req_duration{name:live_detail}', 'p(95)'))],
  ['입찰 평균', (m) => ms(get(m, 'http_req_duration{name:bid}', 'avg'))],
  ['입찰 p95', (m) => ms(get(m, 'http_req_duration{name:bid}', 'p(95)'))],
  ['입찰 성공', (m) => num(get(m, 'bid_success', 'count'))],
  ['정상 거절 (가격 오름·이미 최고)', (m) => num((get(m, 'bid_too_low', 'count') || 0) + (get(m, 'bid_already_top', 'count') || 0))],
  ['입찰 오류', (m) => num(get(m, 'bid_unexpected', 'count'))],
  ['WebSocket 연결 성공률', (m) => pct(get(m, 'ws_connected', 'value'))],
  ['WebSocket 연결 p95', (m) => ms(get(m, 'ws_connect_time', 'p(95)'))],
  ['채팅 퍼지는 시간 평균', (m) => ms(get(m, 'chat_echo_time', 'avg'))],
  ['채팅 퍼지는 시간 p95', (m) => ms(get(m, 'chat_echo_time', 'p(95)'))],
  ['채팅 보냄 / 받음', (m) => `${num(get(m, 'chat_sent', 'count'))} / ${num(get(m, 'chat_received', 'count'))}`],
  ['채팅 토픽 메시지 ÷ 보낸 채팅 (방송 경매 입찰 안내 포함)', (m) => {
    const sent = get(m, 'chat_sent', 'count');
    return sent ? `${(get(m, 'chat_received', 'count') / sent).toFixed(1)}배` : '-';
  }],
  ['채팅 오류', (m) => num(get(m, 'chat_errors', 'count'))],
  ['실시간 경매 메시지 받음', (m) => num(get(m, 'ws_messages', 'count'))],
];

const header = ['항목', ...runs.map((m) => `${num(get(m, 'vus_max', 'value') ?? get(m, 'vus_max', 'max'))}명`)];
const lines = [
  `| ${header.join(' | ')} |`,
  `| ${header.map(() => '---').join(' | ')} |`,
  ...rows.slice(1).map(([label, fn]) => `| ${label} | ${runs.map(fn).join(' | ')} |`),
];
const table = lines.join('\n');
console.log(table);
writeFileSync('results/table.md', `${table}\n`);
console.log('\nresults/table.md에 저장했어요.');
