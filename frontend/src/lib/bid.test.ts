import { describe, expect, it } from 'vitest';
import { bidBase, bidPresets, minimumBid, validateBid } from './bid';

const auction = { startPrice: 10000, currentPrice: 15000, bidUnit: 1000, bidCount: 3 };

describe('minimumBid', () => {
  it('입찰이 없으면 시작가부터, 있으면 현재가 + 최소 입찰 단위부터', () => {
    expect(minimumBid({ ...auction, bidCount: 0, currentPrice: 10000 })).toBe(10000);
    expect(minimumBid(auction)).toBe(16000);
  });
});

describe('bidBase', () => {
  it('입찰이 없으면 시작가, 있으면 현재가', () => {
    expect(bidBase({ ...auction, bidCount: 0, currentPrice: 10000 })).toBe(10000);
    expect(bidBase(auction)).toBe(15000);
  });
});

describe('validateBid', () => {
  it('비었거나 최소 입찰가보다 낮으면 이유를 알려준다', () => {
    expect(validateBid('', auction)).toBe('입찰할 금액을 입력해 주세요.');
    expect(validateBid('15,500', auction)).toBe('16,000 P부터 입찰할 수 있어요.');
    expect(validateBid('16,000', auction)).toBeUndefined();
  });

  it('기준가에서 입찰 단위만큼씩만 올릴 수 있다', () => {
    expect(validateBid('16,500', auction)).toBe('1,000 P 단위로 입찰할 수 있어요.');
    expect(validateBid('20,000', auction)).toBeUndefined();
    // 첫 입찰은 시작가가 기준
    expect(validateBid('10,500', { ...auction, bidCount: 0, currentPrice: 10000 })).toBe('1,000 P 단위로 입찰할 수 있어요.');
  });
});

describe('bidPresets', () => {
  it('[+N]은 현재가 + N이고, 입찰 단위를 한 번 더 더하지 않는다 (5,000 / 10,000 / 30,000)', () => {
    const presets = bidPresets({ startPrice: 10000, currentPrice: 20000, bidUnit: 1000, bidCount: 2 });
    expect(presets).toEqual([
      { label: '최소', amount: 21000 },
      { label: '+5,000', amount: 25000 },
      { label: '+10,000', amount: 30000 },
      { label: '+30,000', amount: 50000 },
    ]);
  });

  it('입찰이 없으면 시작가가 기준이다', () => {
    const presets = bidPresets({ startPrice: 10000, currentPrice: 10000, bidUnit: 1000, bidCount: 0 });
    expect(presets.map((p) => p.amount)).toEqual([10000, 15000, 20000, 40000]);
  });

  it('입찰 단위와 맞지 않거나 최소 입찰가보다 낮은 버튼은 뺀다', () => {
    // 단위 3,000: +5,000·+10,000은 나눠떨어지지 않아 빠지고 +30,000만 남는다
    expect(bidPresets({ ...auction, bidUnit: 3000 }).map((p) => p.label)).toEqual(['최소', '+30,000']);
    // 단위 10,000: +5,000은 최소 입찰가(현재가 + 10,000)보다 낮아 빠진다
    expect(bidPresets({ ...auction, bidUnit: 10000 }).map((p) => p.label)).toEqual(['최소', '+10,000', '+30,000']);
  });

  it('모든 버튼 금액은 단위 규칙을 지킨다', () => {
    for (const p of bidPresets(auction)) expect(validateBid(String(p.amount), auction)).toBeUndefined();
  });
});
