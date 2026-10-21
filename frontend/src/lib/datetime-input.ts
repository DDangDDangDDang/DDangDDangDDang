/*
 * <input type="datetime-local">과 서버 시각 문자열 사이의 변환.
 * 서버는 시간대 없는 서울 시각("2026-10-20T20:00:00")을 주고받는다. (docs/api/README.md)
 */

/** 서버 시각("2026-10-20T20:00:00")을 datetime-local 입력값("2026-10-20T20:00")으로 */
export function toDateTimeInput(serverTime?: string): string {
  return serverTime ? serverTime.slice(0, 16) : '';
}

/** datetime-local 입력값을 서버 시각 문자열로. 비었으면 undefined */
export function fromDateTimeInput(value: string): string | undefined {
  return value ? `${value}:00` : undefined;
}

/** 시각을 datetime-local 입력값 형식으로 (입력의 min·max, 기본값에 쓴다) */
export function dateTimeInputOf(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
