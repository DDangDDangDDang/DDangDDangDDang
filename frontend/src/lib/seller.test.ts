import { describe, expect, it } from 'vitest';
import {
  formatBroadcastDuration,
  formatBusinessNumber,
  formatShippingTime,
  isValidBusinessNumber,
  sellerAuctionEmpty,
  sellerAuctionPriceLabel,
  sellerAuctionQuery,
  soldRate,
} from './seller';

describe('isValidBusinessNumber', () => {
  it('검증 숫자가 맞는 10자리만 통과한다 (하이픈 허용)', () => {
    expect(isValidBusinessNumber('220-81-62517')).toBe(true);
    expect(isValidBusinessNumber('1248100998')).toBe(true);
    expect(isValidBusinessNumber('220-81-62518')).toBe(false);
    expect(isValidBusinessNumber('123456789')).toBe(false);
  });

  it('숫자 10자리를 하이픈 형식으로 보여준다', () => {
    expect(formatBusinessNumber('2208162517')).toBe('220-81-62517');
  });
});

describe('formatBroadcastDuration', () => {
  it('시간과 분으로 보여 준다', () => {
    expect(formatBroadcastDuration(0)).toBe('0분');
    expect(formatBroadcastDuration(30)).toBe('1분 미만');
    expect(formatBroadcastDuration(25 * 60)).toBe('25분');
    expect(formatBroadcastDuration(2 * 3600)).toBe('2시간');
    expect(formatBroadcastDuration(12 * 3600 + 30 * 60 + 59)).toBe('12시간 30분');
  });
});

describe('soldRate', () => {
  it('마감된 경매(낙찰 + 유찰) 중 낙찰 비율, 취소는 빼고 센다', () => {
    expect(soldRate({ soldAuctionCount: 3, failedAuctionCount: 1, canceledAuctionCount: 5 })).toBe(75);
    expect(soldRate({ soldAuctionCount: 1, failedAuctionCount: 2 })).toBe(33);
  });

  it('마감된 경매가 없으면 판단하지 않는다', () => {
    expect(soldRate({ soldAuctionCount: 0, failedAuctionCount: 0, canceledAuctionCount: 2 })).toBeUndefined();
    expect(soldRate(undefined)).toBeUndefined();
  });
});

describe('formatShippingTime', () => {
  it('하루 미만은 시간, 넘으면 일로', () => {
    expect(formatShippingTime(5.5)).toBe('5.5시간');
    expect(formatShippingTime(36)).toBe('1.5일');
    expect(formatShippingTime(undefined)).toBeUndefined();
  });
});

describe('상점 경매 기록 탭', () => {
  it('지난 경매만 유찰·취소 포함 여부를 보낸다', () => {
    expect(sellerAuctionQuery('ONGOING', true)).toEqual({ tab: 'ONGOING' });
    expect(sellerAuctionQuery('READY', true)).toEqual({ tab: 'READY' });
    expect(sellerAuctionQuery('PAST', false)).toEqual({ tab: 'PAST', includeFailedAndCanceled: false });
    expect(sellerAuctionQuery('PAST', true)).toEqual({ tab: 'PAST', includeFailedAndCanceled: true });
  });

  it('가격 이름: 낙찰가·현재가·시작가', () => {
    expect(sellerAuctionPriceLabel('ENDED')).toBe('낙찰가');
    expect(sellerAuctionPriceLabel('ONGOING')).toBe('현재가');
    expect(sellerAuctionPriceLabel('READY')).toBe('시작가');
    expect(sellerAuctionPriceLabel('FAILED')).toBe('시작가');
    expect(sellerAuctionPriceLabel('CANCELED')).toBe('시작가');
  });

  it('지난 경매가 비었을 때 유찰·취소를 숨겼으면 켜는 방법을 알려 준다', () => {
    expect(sellerAuctionEmpty('PAST', false).description).toContain('유찰·취소 포함');
    expect(sellerAuctionEmpty('PAST', true).title).toBe('지난 경매가 없어요');
  });
});
