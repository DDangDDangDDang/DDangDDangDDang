import { parseAmount } from './auction';

/** 충전 금액 바로 고르기 */
export const CHARGE_PRESETS = [1_000, 10_000, 50_000] as const;
/** 한 번에 충전할 수 있는 금액 (원 = 포인트). 서버는 0보다 크면 받지만, 결제 수단 최소 금액과 실수 방지를 위해 화면에서 정한다. */
export const CHARGE_MIN = 1_000;
export const CHARGE_MAX = 1_000_000;

/** 충전 금액 입력을 확인한다. 문제가 없으면 undefined */
export function validateChargeAmount(value: string): string | undefined {
  const amount = parseAmount(value);
  if (!amount) return '충전할 금액을 입력해 주세요.';
  if (amount < CHARGE_MIN) return `${CHARGE_MIN.toLocaleString('ko-KR')}원부터 충전할 수 있어요.`;
  if (amount > CHARGE_MAX) return `한 번에 ${CHARGE_MAX.toLocaleString('ko-KR')}원까지 충전할 수 있어요.`;
  return undefined;
}

export type PortOneConfig = { storeId: string; channelKey: string };

/**
 * 포트원 결제창 설정 (frontend/.env.local의 VITE_PORTONE_STORE_ID, VITE_PORTONE_CHANNEL_KEY).
 * 둘 다 포트원 콘솔에서 보이는 공개 값이다. 없으면 undefined (결제창을 띄우지 않고 "충전 설정이 필요합니다"라고 안내한다)
 */
export function portOneConfig(env: Record<string, string | undefined> = import.meta.env): PortOneConfig | undefined {
  const storeId = env.VITE_PORTONE_STORE_ID?.trim();
  const channelKey = env.VITE_PORTONE_CHANNEL_KEY?.trim();
  return storeId && channelKey ? { storeId, channelKey } : undefined;
}

/**
 * 충전 결과 (POST /points/charges/complete의 status)
 * done: 포인트 지급됨 · pending: 결제 확인 중(가상계좌 입금 대기 등) · failed: 결제 실패·취소
 */
export type ChargeOutcome =
  | { kind: 'done'; amount: number; balance?: number }
  | { kind: 'pending'; amount: number }
  | { kind: 'failed'; message: string };

export function chargeOutcomeOf(result: { status?: 'PENDING' | 'COMPLETE' | 'FAILED'; amount?: number; balance?: number } | undefined): ChargeOutcome {
  const amount = result?.amount ?? 0;
  if (result?.status === 'COMPLETE') return { kind: 'done', amount, balance: result.balance };
  if (result?.status === 'PENDING') return { kind: 'pending', amount };
  return { kind: 'failed', message: '결제가 완료되지 않아 충전하지 못했어요. 결제된 금액이 있다면 자동으로 취소돼요.' };
}
