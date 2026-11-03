import { describe, expect, it } from 'vitest';
import { isDeliveryFilled, isValidReceiverPhone } from './AddressFields';

describe('배송지 입력 검사', () => {
  it('연락처는 서버와 같이 0으로 시작하는 9~11자리만 받는다', () => {
    expect(isValidReceiverPhone('01012345678')).toBe(true);
    expect(isValidReceiverPhone('021234567')).toBe(true);
    expect(isValidReceiverPhone('1')).toBe(false);
    expect(isValidReceiverPhone('0101234')).toBe(false);
    expect(isValidReceiverPhone('11012345678')).toBe(false);
  });

  it('연락처 형식이 틀리면 제출할 수 없다', () => {
    const filled = { receiverName: '홍길동', receiverPhone: '01012345678', address: '서울시 중구 세종대로 1', deliveryMemo: '' };
    expect(isDeliveryFilled(filled)).toBe(true);
    expect(isDeliveryFilled({ ...filled, receiverPhone: '1' })).toBe(false);
    expect(isDeliveryFilled({ ...filled, address: '  ' })).toBe(false);
  });
});
