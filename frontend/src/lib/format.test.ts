import { describe, expect, it } from 'vitest';
import { formatDotDate, formatNumber, formatPhoneInput, formatPoint, formatRemaining } from './format';

describe('format', () => {
  it('입력 중인 전화번호에 하이픈을 붙인다', () => {
    expect(formatPhoneInput('010')).toBe('010');
    expect(formatPhoneInput('0101234')).toBe('010-123-4');
    expect(formatPhoneInput('01012345678')).toBe('010-1234-5678');
    expect(formatPhoneInput('0311234567')).toBe('031-123-4567');
    expect(formatPhoneInput('021234567')).toBe('02-123-4567');
    expect(formatPhoneInput('0212345678')).toBe('02-1234-5678');
    expect(formatPhoneInput('010-12a3')).toBe('010-123');
  });

  it('포인트는 천 단위 구분과 P를 붙인다', () => {
    expect(formatPoint(152000)).toBe('152,000 P');
    expect(formatNumber(1284)).toBe('1,284');
  });

  it('남은 시간은 mm:ss, 1시간 이상이면 h:mm:ss', () => {
    expect(formatRemaining(48)).toBe('00:48');
    expect(formatRemaining(150)).toBe('02:30');
    expect(formatRemaining(3725)).toBe('1:02:05');
    expect(formatRemaining(-3)).toBe('00:00');
    // 하루 이상이면 일을 따로
    expect(formatRemaining(86400)).toBe('1일 00:00:00');
    expect(formatRemaining(3 * 86400 - 1)).toBe('2일 23:59:59');
  });
});

describe('formatDotDate', () => {
  it('점으로 나눈 날짜로 보여 준다', () => {
    expect(formatDotDate('2026-10-21T09:05:00')).toBe('2026.10.21');
    expect(formatDotDate('2026-01-03T00:00:00')).toBe('2026.01.03');
  });
});
