import { describe, expect, it } from 'vitest';
import { dateTimeInputOf, fromDateTimeInput, toDateTimeInput } from './datetime-input';

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
});
