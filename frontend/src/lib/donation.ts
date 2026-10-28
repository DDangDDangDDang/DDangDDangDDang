import type { Schema } from '@/api';
import { formatMileage } from '@/lib/format';

/*
 * 도네이션 마일리지: 구매·판매 확정으로 적립하고, 라이브 방송 중인 판매자에게 선물하거나 상점에서 쓰는 마일리지 (현금 포인트와 따로, 환전할 수 없음)
 * 구매·판매 적립과 선물받은 마일리지는 구분 없이 하나의 잔액(balance)으로 쓴다.
 */

export type DonationStatus = Schema<'DonationStatusResponse'>;
export type DonationHistoryType = NonNullable<Schema<'DonationPointHistoryResponse'>['type']>;

/** 내 도네이션 마일리지 (헤더·내역 화면·선물 창이 같은 캐시를 쓴다) */
export const DONATION_WALLET_KEY = ['donation', 'wallet'] as const;
export const donationStatusKey = (liveStreamId: number) => ['donation', 'status', liveStreamId] as const;
export const DONATION_HISTORY_KEY = ['donation', 'histories'] as const;

export const DONATION_HISTORY_LABEL: Record<DonationHistoryType, string> = {
  EARN: '구매 확정 적립',
  SALE: '판매 확정 적립',
  GIFT: '선물함',
  RECEIVE: '선물받음',
  EXPIRE: '유효기간 만료',
  SPEND: '판매자 상점',
};

/** 늘어나는(+) 내역인지. 선물함·만료·상점 구매는 줄어든다(−). */
export const DONATION_HISTORY_INCREASES: Record<DonationHistoryType, boolean> = {
  EARN: true,
  SALE: true,
  GIFT: false,
  RECEIVE: true,
  EXPIRE: false,
  SPEND: false,
};

/** 선물 창 빠른 금액 버튼. 최대 선물 이하만 보여 준다. */
export function giftPresets(status: Pick<DonationStatus, 'maxGift' | 'giftUnit'>): number[] {
  const unit = status.giftUnit ?? 100;
  const max = status.maxGift ?? 0;
  return [100, 500, 1_000, 3_000, 5_000].filter((amount) => amount <= max && amount % unit === 0);
}

/** 선물할 수 없는 금액이면 그 이유 (화면 문구). 보낼 수 있으면 undefined */
export function validateGift(amount: number | undefined, status: DonationStatus): string | undefined {
  const min = status.minGift ?? 0;
  const max = status.maxGift ?? 0;
  const unit = status.giftUnit ?? 1;
  if (amount === undefined || amount <= 0) return '선물할 마일리지를 입력해 주세요.';
  if (amount < min || amount > max || amount % unit !== 0) {
    return `${formatMileage(min)}부터 ${formatMileage(max)}까지 ${formatMileage(unit)} 단위로 선물할 수 있어요.`;
  }
  if (amount > (status.remainingThisMonth ?? 0)) {
    return `이 판매자에게는 이번 달 ${formatMileage(status.remainingThisMonth ?? 0)}까지 더 선물할 수 있어요.`;
  }
  if (amount > (status.balance ?? 0)) {
    return '마일리지가 부족해요. 구매·판매를 확정하면 도네이션 마일리지가 쌓여요.';
  }
  return undefined;
}

/** 선물 하나마다 새로 만드는 요청 키. 응답을 못 받아 다시 보낼 때만 같은 키를 쓴다. (두 번 선물되지 않게) */
export function newRequestKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
