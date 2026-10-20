import { describe, expect, it } from 'vitest';
import { formatNumber, formatPoint, formatRemaining } from './format';

describe('format', () => {
  it('포인트는 천 단위 구분과 P를 붙인다', () => {
    expect(formatPoint(152000)).toBe('152,000 P');
    expect(formatNumber(1284)).toBe('1,284');
  });

  it('남은 시간은 mm:ss, 1시간 이상이면 h:mm:ss', () => {
    expect(formatRemaining(48)).toBe('00:48');
    expect(formatRemaining(150)).toBe('02:30');
    expect(formatRemaining(3725)).toBe('1:02:05');
    expect(formatRemaining(-3)).toBe('00:00');
  });
});
