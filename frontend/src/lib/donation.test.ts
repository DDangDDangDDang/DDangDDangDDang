import { describe, expect, it } from 'vitest';
import { giftPresets, validateGift, type DonationStatus } from './donation';

const status: DonationStatus = {
  sellerId: 2,
  balance: 2_000,
  monthlyLimit: 5_000,
  givenThisMonth: 4_000,
  remainingThisMonth: 1_000,
  minGift: 100,
  giftUnit: 100,
  maxGift: 5_000,
};

describe('donation', () => {
  it('선물 금액: 비었거나, 최소·단위·최대를 벗어나거나, 이번 달 한도·마일리지를 넘으면 이유를 알려 준다', () => {
    expect(validateGift(undefined, status)).toBe('선물할 마일리지를 입력해 주세요.');
    expect(validateGift(150, status)).toBe('100 M부터 5,000 M까지 100 M 단위로 선물할 수 있어요.');
    expect(validateGift(5_100, status)).toContain('단위로 선물할 수 있어요');
    expect(validateGift(1_500, status)).toBe('이 판매자에게는 이번 달 1,000 M까지 더 선물할 수 있어요.');
    expect(validateGift(1_000, { ...status, balance: 500 })).toBe('마일리지가 부족해요. 구매·판매를 확정하면 도네이션 마일리지가 쌓여요.');
    expect(validateGift(1_000, status)).toBeUndefined();
  });

  it('빠른 금액 버튼은 최대 선물 이하, 선물 단위에 맞는 것만', () => {
    expect(giftPresets(status)).toEqual([100, 500, 1_000, 3_000, 5_000]);
    expect(giftPresets({ maxGift: 1_000, giftUnit: 500 })).toEqual([500, 1_000]);
  });
});
