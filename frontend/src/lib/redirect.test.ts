import { describe, expect, it } from 'vitest';
import { safeRedirect } from './redirect';

describe('safeRedirect', () => {
  it('우리 사이트 경로는 그대로 돌려준다', () => {
    expect(safeRedirect('/orders?role=SELLER')).toBe('/orders?role=SELLER');
    expect(safeRedirect('/points#history')).toBe('/points#history');
  });

  it('외부 주소나 잘못된 값은 홈으로 보낸다', () => {
    expect(safeRedirect(null)).toBe('/');
    expect(safeRedirect('')).toBe('/');
    expect(safeRedirect('https://evil.com')).toBe('/');
    expect(safeRedirect('//evil.com')).toBe('/');
    expect(safeRedirect('/\\evil.com')).toBe('/'); // 브라우저는 "//evil.com"으로 읽는다
    expect(safeRedirect('/\tevil.com')).toBe('/');
    expect(safeRedirect('javascript:alert(1)')).toBe('/');
  });
});
