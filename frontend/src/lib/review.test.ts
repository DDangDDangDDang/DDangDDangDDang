import { describe, expect, it } from 'vitest';
import { formatRating, isOwnStore, reviewSummary, satisfactionLabel } from './review';

describe('리뷰 표시 계산', () => {
  it('평균 별점은 소수 첫째 자리까지, 없으면 undefined', () => {
    expect(formatRating(4.3)).toBe('4.3');
    expect(formatRating(4)).toBe('4.0');
    expect(formatRating(null)).toBeUndefined();
    expect(formatRating(undefined)).toBeUndefined();
  });

  it('구매자 만족도: 평균과 개수, 받은 리뷰가 없으면 "아직 없음"', () => {
    expect(satisfactionLabel({ averageRating: 4.3, reviewCount: 3 })).toBe('★ 4.3 / 리뷰 3개');
    expect(satisfactionLabel({ averageRating: 4.5, reviewCount: 1200 })).toBe('★ 4.5 / 리뷰 1,200개');
    expect(satisfactionLabel({ averageRating: null as unknown as undefined, reviewCount: 0 })).toBe('아직 없음');
    expect(satisfactionLabel(undefined)).toBe('아직 없음');
  });

  it('리뷰 탭 요약: "★ 4.3 · 리뷰 3개", 받은 리뷰가 없으면 undefined', () => {
    expect(reviewSummary({ averageRating: 4.3, reviewCount: 3 })).toBe('★ 4.3 · 리뷰 3개');
    expect(reviewSummary({ reviewCount: 0 })).toBeUndefined();
  });

  it('로그인한 회원이 판매자 본인일 때만 내 상점이다', () => {
    expect(isOwnStore(7, 7)).toBe(true);
    expect(isOwnStore(7, 8)).toBe(false);
    expect(isOwnStore(7, undefined)).toBe(false);
  });
});
