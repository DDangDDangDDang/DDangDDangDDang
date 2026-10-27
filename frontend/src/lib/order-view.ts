import type { Schema } from '@/api';

type Order = Schema<'OrderResponse'>;

export type OrderRole = 'BUYER' | 'SELLER';

/**
 * 주문 상세에서 보여줄 단계.
 * - NEED_ADDRESS: 낙찰됨, 배송지 없음
 * - WAITING_SHIPMENT: 낙찰됨, 배송지 있음 (발송 대기)
 * - SHIPPING: 배송 중
 * - COMPLETED: 구매 확정 완료 (판매자는 정산 완료)
 */
export type OrderStage = 'NEED_ADDRESS' | 'WAITING_SHIPMENT' | 'SHIPPING' | 'COMPLETED';

/** 로그인한 회원이 이 주문의 구매자인지 판매자인지. 둘 다 아니면 null */
export function orderRoleOf(order: Order, userId: number | undefined): OrderRole | null {
  if (userId === undefined) return null;
  if (order.buyerId === userId) return 'BUYER';
  if (order.sellerId === userId) return 'SELLER';
  return null;
}

export function hasDeliveryAddress(order: Order): boolean {
  return Boolean(order.receiverName && order.receiverPhone && order.address);
}

export function orderStage(order: Order): OrderStage {
  switch (order.status) {
    case 'SHIPPING':
      return 'SHIPPING';
    case 'COMPLETED':
      return 'COMPLETED';
    default:
      return hasDeliveryAddress(order) ? 'WAITING_SHIPMENT' : 'NEED_ADDRESS';
  }
}

/** API 호출에 쓸 주문 ID. 서버 응답에는 항상 있지만, 타입상 선택값이라 없으면 오류로 알린다. */
export function requireOrderId(order: Order): number {
  if (order.orderId == null) throw new Error('주문 정보를 다시 불러와 주세요.');
  return order.orderId;
}
