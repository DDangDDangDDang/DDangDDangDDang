import type { Schema } from '@/api';

/*
 * 서버 enum 값을 화면 문구로 바꾸는 표. 화면에서는 서버 값(ESCROW 등)을 그대로 보여주지 않는다.
 * Record로 선언해 두어, 서버에 값이 추가되면 타입 오류로 알 수 있다.
 */

export type PointHistoryType = NonNullable<Schema<'PointHistoryResponse'>['type']>;
export type OrderStatus = NonNullable<Schema<'OrderResponse'>['status']>;
export type Courier = NonNullable<Schema<'OrderResponse'>['courier']>;

export const POINT_HISTORY_LABEL: Record<PointHistoryType, string> = {
  CHARGE: '충전',
  HOLD: '입찰(묶임)',
  RELEASE: '입찰 해제',
  PURCHASE: '낙찰·구매',
  SETTLE: '판매 정산',
  EXCHANGE: '환전',
  EXCHANGE_REFUND: '환전 실패 환불',
};

/** 사용 가능 포인트 기준으로 늘어나는(+) 종류인지. 묶임·구매·환전은 줄어든다(−). */
export const POINT_HISTORY_INCREASES: Record<PointHistoryType, boolean> = {
  CHARGE: true,
  HOLD: false,
  RELEASE: true,
  PURCHASE: false,
  SETTLE: true,
  EXCHANGE: false,
  EXCHANGE_REFUND: true,
};

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  ESCROW: '낙찰됨(발송 전)',
  SHIPPING: '배송 중',
  COMPLETED: '완료',
};

export const COURIER_LABEL: Record<Courier, string> = {
  CJ: 'CJ대한통운',
  LOTTE: '롯데택배',
  HANJIN: '한진택배',
  LOGEN: '로젠택배',
  POST: '우체국택배',
  ETC: '기타',
};

export type ExchangeStatus = NonNullable<Schema<'PointExchangeResponse'>['status']>;

export const EXCHANGE_STATUS_LABEL: Record<ExchangeStatus, string> = {
  PENDING: '신청됨',
  PROCESSING: '송금 중',
  COMPLETE: '송금 완료',
  FAILED: '송금 실패',
  CANCELLED: '취소됨',
};

/** 환전 받을 은행 (금융결제원 은행 코드 3자리). 서버는 코드 형식(숫자 3자리)만 검사한다. */
export const BANK_LABEL: Record<string, string> = {
  '004': 'KB국민은행',
  '088': '신한은행',
  '020': '우리은행',
  '081': '하나은행',
  '011': 'NH농협은행',
  '003': 'IBK기업은행',
  '090': '카카오뱅크',
  '092': '토스뱅크',
  '089': '케이뱅크',
  '023': 'SC제일은행',
  '027': '한국씨티은행',
  '071': '우체국',
  '045': '새마을금고',
  '048': '신협',
  '007': '수협은행',
  '031': 'iM뱅크(대구)',
  '032': '부산은행',
  '039': '경남은행',
  '034': '광주은행',
  '037': '전북은행',
  '035': '제주은행',
};
