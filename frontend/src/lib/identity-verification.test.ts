import { describe, expect, it } from 'vitest';
import { formatBirthDate, identityConfig, newIdentityVerificationId } from './identity-verification';

describe('identityConfig', () => {
  it('상점 아이디와 본인인증 채널 키가 모두 있어야 설정이 있다 (결제 채널 키로는 대신하지 않는다)', () => {
    expect(identityConfig({ VITE_PORTONE_STORE_ID: 'store-1', VITE_PORTONE_IDENTITY_CHANNEL_KEY: ' channel-1 ' })).toEqual({
      storeId: 'store-1',
      channelKey: 'channel-1',
    });
    expect(identityConfig({ VITE_PORTONE_STORE_ID: 'store-1', VITE_PORTONE_CHANNEL_KEY: 'pay-channel' })).toBeUndefined();
    expect(identityConfig({})).toBeUndefined();
  });
});

describe('newIdentityVerificationId', () => {
  it('부를 때마다 다른 ID를 만든다', () => {
    expect(newIdentityVerificationId()).toMatch(/^identity-/);
    expect(newIdentityVerificationId()).not.toBe(newIdentityVerificationId());
  });
});

describe('formatBirthDate', () => {
  it('yyyy-MM-dd를 점으로 나눠 보여 준다', () => {
    expect(formatBirthDate('1990-01-31')).toBe('1990.01.31');
    expect(formatBirthDate(undefined)).toBe('-');
  });
});
