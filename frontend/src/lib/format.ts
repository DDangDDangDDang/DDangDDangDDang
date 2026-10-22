/** 포인트 금액: 152000 → "152,000 P" */
export function formatPoint(amount: number): string {
  return `${amount.toLocaleString('ko-KR')} P`;
}

/** 숫자만 천 단위 구분: 1284 → "1,284" */
export function formatNumber(value: number): string {
  return value.toLocaleString('ko-KR');
}

/** 남은 초를 "mm:ss"로 (1시간 이상이면 "h:mm:ss", 하루 이상이면 "N일 hh:mm:ss"): 48 → "00:48" */
export function formatRemaining(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mmss = `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
  // 진행 시간이 3일까지라 "71:59:59"처럼 읽기 어려운 시간 대신 일을 따로 보여준다.
  if (d > 0) return `${d}일 ${String(h).padStart(2, '0')}:${mmss}`;
  return h > 0 ? `${h}:${mmss}` : mmss;
}

/** 서버 날짜(시각)를 "2026. 10. 20." 형식으로 */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR');
}

/** 휴대폰 번호에 하이픈: 01012345678 → "010-1234-5678" */
export function formatPhone(phone: string): string {
  const digits = phone.replace(/\D/g, '');
  if (digits.length === 11) return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
  if (digits.length === 10) return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
  return phone;
}

/** 입력값에서 숫자만 남긴다. (휴대폰 번호, 인증번호, 송장번호 입력) */
export function onlyDigits(value: string): string {
  return value.replace(/\D/g, '');
}

/**
 * 서버 시각 문자열을 한국어 날짜·시간으로: "2026-10-20T20:00:00" → "10월 20일 (화) 20:00"
 * 서버는 시간대 없는 서울 시각을 보낸다. (docs/api/README.md)
 */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}
