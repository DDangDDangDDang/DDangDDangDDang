import { describe, expect, it } from 'vitest';
import { chargeOutcomeOf, portOneConfig, validateChargeAmount } from './point-charge';

describe('validateChargeAmount', () => {
  it('비었거나 범위를 벗어나면 이유를 알려준다', () => {
    expect(validateChargeAmount('')).toBe('충전할 금액을 입력해 주세요.');
    expect(validateChargeAmount('500')).toBe('1,000원부터 충전할 수 있어요.');
    expect(validateChargeAmount('1,000,001')).toBe('한 번에 1,000,000원까지 충전할 수 있어요.');
  });

  it('쉼표가 있어도 금액으로 읽는다', () => {
    expect(validateChargeAmount('30,000')).toBeUndefined();
  });
});

describe('portOneConfig', () => {
  it('상점 ID와 채널 키가 모두 있어야 설정으로 본다', () => {
    expect(portOneConfig({ VITE_PORTONE_STORE_ID: 'store-1', VITE_PORTONE_CHANNEL_KEY: 'channel-key-1' })).toEqual({ storeId: 'store-1', channelKey: 'channel-key-1' });
    expect(portOneConfig({ VITE_PORTONE_STORE_ID: 'store-1' })).toBeUndefined();
    expect(portOneConfig({ VITE_PORTONE_STORE_ID: ' ', VITE_PORTONE_CHANNEL_KEY: 'channel-key-1' })).toBeUndefined();
  });
});

describe('chargeOutcomeOf', () => {
  it('서버 상태로 결과를 나눈다', () => {
    expect(chargeOutcomeOf({ status: 'COMPLETE', amount: 10000, balance: 25000 })).toEqual({ kind: 'done', amount: 10000, balance: 25000 });
    expect(chargeOutcomeOf({ status: 'PENDING', amount: 10000 })).toEqual({ kind: 'pending', amount: 10000 });
    expect(chargeOutcomeOf({ status: 'FAILED', amount: 10000 }).kind).toBe('failed');
  });
});
