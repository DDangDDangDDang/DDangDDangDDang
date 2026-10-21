import { describe, expect, it } from 'vitest';
import { dateTimeInputOf, fromDateTimeInput, seoulTimeMillis, toDateTimeInput } from './datetime-input';

describe('datetime-input', () => {
  it('서버 시각과 입력값을 서로 바꾼다', () => {
    expect(toDateTimeInput('2026-10-21T20:00:00')).toBe('2026-10-21T20:00');
    expect(fromDateTimeInput('2026-10-21T20:00')).toBe('2026-10-21T20:00:00');
    expect(toDateTimeInput(undefined)).toBe('');
    expect(fromDateTimeInput('')).toBeUndefined();
  });

  it('시각을 입력값 형식으로 바꾼다', () => {
    expect(dateTimeInputOf(new Date(2026, 0, 5, 7, 3))).toBe('2026-01-05T07:03');
  });

  it('시간대 없는 서버 시각은 브라우저 시간대와 상관없이 서울 시각으로 읽는다', () => {
    expect(seoulTimeMillis('2026-10-21T20:30:00')).toBe(Date.parse('2026-10-21T11:30:00Z'));
    expect(seoulTimeMillis('2026-10-21T11:30:00Z')).toBe(Date.parse('2026-10-21T11:30:00Z'));
  });
});
