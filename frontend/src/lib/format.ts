/** 포인트 금액: 152000 → "152,000 P" */
export function formatPoint(amount: number): string {
  return `${amount.toLocaleString('ko-KR')} P`;
}

/** 숫자만 천 단위 구분: 1284 → "1,284" */
export function formatNumber(value: number): string {
  return value.toLocaleString('ko-KR');
}

/** 남은 초를 "mm:ss"로 (1시간 이상이면 "h:mm:ss"): 48 → "00:48" */
export function formatRemaining(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  const sec = s % 60;
  const mmss = `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
  return h > 0 ? `${h}:${mmss}` : mmss;
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
