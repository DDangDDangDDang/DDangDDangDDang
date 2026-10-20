import { describe, expect, it } from 'vitest';
import { orderRoleOf, orderStage } from './order-view';

const base = { orderId: 1, buyerId: 10, sellerId: 20, status: 'ESCROW' as const };
const address = { receiverName: '홍길동', receiverPhone: '01012345678', address: '서울시 중구 세종대로 1' };

describe('주문 상세 화면 판단', () => {
  it('로그인한 회원이 구매자인지 판매자인지 구분한다', () => {
    expect(orderRoleOf(base, 10)).toBe('BUYER');
    expect(orderRoleOf(base, 20)).toBe('SELLER');
    expect(orderRoleOf(base, 30)).toBeNull();
    expect(orderRoleOf(base, undefined)).toBeNull();
  });

  it('낙찰됨 상태는 배송지 유무로 단계를 나눈다', () => {
    expect(orderStage(base)).toBe('NEED_ADDRESS');
    expect(orderStage({ ...base, receiverName: '홍길동' })).toBe('NEED_ADDRESS');
    expect(orderStage({ ...base, ...address })).toBe('WAITING_SHIPMENT');
  });

  it('배송 중·완료는 상태 그대로 따른다', () => {
    expect(orderStage({ ...base, ...address, status: 'SHIPPING' })).toBe('SHIPPING');
    expect(orderStage({ ...base, ...address, status: 'COMPLETED' })).toBe('COMPLETED');
  });
});
