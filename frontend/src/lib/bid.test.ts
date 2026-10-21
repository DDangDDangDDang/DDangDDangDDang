import { describe, expect, it } from 'vitest';
import { minimumBid, validateBid } from './bid';

const auction = { startPrice: 10000, currentPrice: 15000, bidUnit: 1000, bidCount: 3 };

describe('minimumBid', () => {
  it('입찰이 없으면 시작가부터, 있으면 현재가 + 최소 입찰 단위부터', () => {
    expect(minimumBid({ ...auction, bidCount: 0, currentPrice: 10000 })).toBe(10000);
    expect(minimumBid(auction)).toBe(16000);
  });
});

describe('validateBid', () => {
  it('비었거나 최소 입찰가보다 낮으면 이유를 알려준다', () => {
    expect(validateBid('', auction)).toBe('입찰할 금액을 입력해 주세요.');
    expect(validateBid('15,500', auction)).toBe('16,000 P부터 입찰할 수 있어요.');
    expect(validateBid('16,000', auction)).toBeUndefined();
  });
});
