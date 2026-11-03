import { describe, expect, it } from 'vitest';
import { autoConfirmAt, autoConfirmMessage, orderCancelMessage, orderDeadlineMessage, orderRoleOf, orderStage } from './order-view';

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

  it('취소된 주문은 배송지가 있어도 취소 단계다', () => {
    expect(orderStage({ ...base, status: 'CANCELED' })).toBe('CANCELED');
    expect(orderStage({ ...base, ...address, status: 'CANCELED' })).toBe('CANCELED');
  });

  it('배송지 기한 안내에 위약금을 알린다', () => {
    const order = { ...base, finalPrice: 100_000, noAddressPenalty: 10_000, addressDeadlineAt: '2026-11-05T12:00:00' };
    expect(orderDeadlineMessage(order)).toContain('위약금 10,000 P');
  });

  it('위약금 없는 기존 주문은 전액 환불로 안내한다', () => {
    const order = { ...base, finalPrice: 100_000, noAddressPenalty: 0, addressDeadlineAt: '2026-11-05T12:00:00' };
    expect(orderDeadlineMessage(order)).toContain('전액 환불');
    expect(orderDeadlineMessage(order)).not.toContain('위약금');
  });

  it('발송 기한은 위약금 없이 전액 환불로 안내하고, 기한이 없으면 안내하지 않는다', () => {
    const order = { ...base, ...address, noAddressPenalty: 10_000, shipDeadlineAt: '2026-11-09T12:00:00' };
    expect(orderDeadlineMessage(order)).toContain('전액 환불');
    expect(orderDeadlineMessage({ ...base })).toBeNull();
  });

  it('낙찰취소 안내는 위약금과 환불액을 보여 준다', () => {
    const order = { ...base, status: 'CANCELED' as const, cancelReason: 'BUYER_NO_ADDRESS' as const, finalPrice: 100_000, noAddressPenalty: 10_000 };
    expect(orderCancelMessage(order)).toContain('낙찰취소');
    expect(orderCancelMessage(order)).toContain('위약금 10,000 P');
    expect(orderCancelMessage(order)).toContain('90,000 P');
  });

  it('제품 미발송 취소는 위약금 없이 전액 환불이다', () => {
    const order = { ...base, status: 'CANCELED' as const, cancelReason: 'SELLER_NOT_SHIPPED' as const, finalPrice: 100_000, noAddressPenalty: 10_000 };
    expect(orderCancelMessage(order)).toContain('전액 환불');
    expect(orderCancelMessage(order)).not.toContain('위약금');
  });

  it('배송 중·완료는 상태 그대로 따른다', () => {
    expect(orderStage({ ...base, ...address, status: 'SHIPPING' })).toBe('SHIPPING');
    expect(orderStage({ ...base, ...address, status: 'COMPLETED' })).toBe('COMPLETED');
  });

  it('배송 중 주문은 발송 7일 뒤를 자동 확정 예정 시각으로 안내한다', () => {
    const order = { ...base, ...address, status: 'SHIPPING' as const, shippedAt: '2026-10-28T15:30:00' };
    expect(autoConfirmAt(order)).toBe('2026-11-04T15:30:00');
    expect(autoConfirmMessage(order)).toContain('발송 후 7일이 지나면 자동으로 구매 확정돼요');
    expect(autoConfirmMessage(order)).toContain('11월 4일');
  });

  it('발송 시각이 없거나 배송 중이 아니면 예정 시각 없이 안내한다', () => {
    expect(autoConfirmAt({ ...base, status: 'SHIPPING' })).toBeNull();
    expect(autoConfirmAt({ ...base, status: 'COMPLETED', shippedAt: '2026-10-28T15:30:00' })).toBeNull();
    expect(autoConfirmMessage({ ...base, status: 'SHIPPING' })).toBe('발송 후 7일이 지나면 자동으로 구매 확정돼요.');
  });
});
