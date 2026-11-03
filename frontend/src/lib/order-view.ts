import type { Schema } from '@/api';
import { dateTimeInputOf } from './datetime-input';
import { formatDateTime, formatPoint } from './format';
import { ORDER_CANCEL_REASON_LABEL } from './labels';

type Order = Schema<'OrderResponse'>;

export type OrderRole = 'BUYER' | 'SELLER';

/**
 * 주문 상세에서 보여줄 단계.
 * - NEED_ADDRESS: 낙찰됨, 배송지 없음
 * - WAITING_SHIPMENT: 낙찰됨, 배송지 있음 (발송 대기)
 * - SHIPPING: 배송 중
 * - COMPLETED: 구매 확정 완료 (판매자는 정산 완료)
 * - CANCELED: 기한 초과로 취소, 구매자에게 환불
 */
export type OrderStage = 'NEED_ADDRESS' | 'WAITING_SHIPMENT' | 'SHIPPING' | 'COMPLETED' | 'CANCELED';

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
    case 'CANCELED':
      return 'CANCELED';
    default:
      return hasDeliveryAddress(order) ? 'WAITING_SHIPMENT' : 'NEED_ADDRESS';
  }
}

/** 발송 전 주문의 기한 안내. 기한이 없는 도입 전 주문이나 발송 뒤에는 null */
export function orderDeadlineMessage(order: Order): string | null {
  const stage = orderStage(order);
  if (stage === 'NEED_ADDRESS' && order.addressDeadlineAt) {
    const penalty = order.noAddressPenalty ?? 0;
    const result =
      penalty > 0
        ? `기한이 지나면 낙찰이 취소되고 위약금 ${formatPoint(penalty)}는 판매자에게 지급돼요.`
        : '기한이 지나면 낙찰이 취소되고 구매자에게 전액 환불돼요.';
    return `배송지 입력 기한: ${formatDateTime(order.addressDeadlineAt)}까지. ${result}`;
  }
  if (stage === 'WAITING_SHIPMENT' && order.shipDeadlineAt) {
    return `발송 기한: ${formatDateTime(order.shipDeadlineAt)}까지. 기한이 지나면 주문이 취소되고 구매자에게 전액 환불돼요.`;
  }
  return null;
}

/** 발송 후 이 날수가 지나면 서버가 자동으로 구매 확정한다. (OrderService.AUTO_CONFIRM_DAYS) */
export const AUTO_CONFIRM_DAYS = 7;

/** 배송 중 주문의 자동 구매 확정 예정 시각(서버 시각 문자열). 발송 시각이 없으면 null */
export function autoConfirmAt(order: Order): string | null {
  if (order.status !== 'SHIPPING' || !order.shippedAt) return null;
  // 서버 시각은 시간대 없는 서울 시각이라, 같은 방식으로 읽고 써야 formatDateTime과 어긋나지 않는다.
  const date = new Date(order.shippedAt);
  if (Number.isNaN(date.getTime())) return null;
  date.setDate(date.getDate() + AUTO_CONFIRM_DAYS);
  return `${dateTimeInputOf(date)}:00`;
}

/** 배송 중 주문의 자동 구매 확정 안내 */
export function autoConfirmMessage(order: Order): string {
  const at = autoConfirmAt(order);
  const base = `발송 후 ${AUTO_CONFIRM_DAYS}일이 지나면 자동으로 구매 확정돼요.`;
  return at ? `${base} (${formatDateTime(at)} 자동 확정 예정)` : base;
}

/** 취소된 주문의 안내. 배송지 미입력 취소만 위약금이 있다 */
export function orderCancelMessage(order: Order): string {
  const title = order.cancelReason ? `${ORDER_CANCEL_REASON_LABEL[order.cancelReason]} 처리된 주문이에요.` : '취소된 주문이에요.';
  const penalty = order.cancelReason === 'BUYER_NO_ADDRESS' ? (order.noAddressPenalty ?? 0) : 0;
  if (penalty === 0) return `${title} 결제 포인트는 구매자에게 전액 환불됐어요.`;
  return `${title} 위약금 ${formatPoint(penalty)}는 판매자에게 지급되고, ${formatPoint((order.finalPrice ?? 0) - penalty)}는 구매자에게 환불됐어요.`;
}

/** API 호출에 쓸 주문 ID. 서버 응답에는 항상 있지만, 타입상 선택값이라 없으면 오류로 알린다. */
export function requireOrderId(order: Order): number {
  if (order.orderId == null) throw new Error('주문 정보를 다시 불러와 주세요.');
  return order.orderId;
}
