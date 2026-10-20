import type { Schema } from '@/api';
import { BANK_LABEL } from './labels';

/*
 * 포인트 환전 신청 입력 검사. (화면과 분리해 단위 테스트한다)
 * 서버 규칙: 금액 1,000~1,000,000 P(POINT_044), 은행 코드 숫자 3자리, 계좌번호 숫자·하이픈 10~20자, 예금주 30자 이하
 */

export const EXCHANGE_MIN = 1_000;
export const EXCHANGE_MAX = 1_000_000;
export const ACCOUNT_HOLDER_MAX_LENGTH = 30;

export type ExchangeFormValues = { amount: string; bankCode: string; accountNumber: string; accountHolder: string };
export type ExchangeFormErrors = Partial<Record<keyof ExchangeFormValues, string>>;

export const EMPTY_EXCHANGE_FORM: ExchangeFormValues = { amount: '', bankCode: '', accountNumber: '', accountHolder: '' };

function amountOf(value: string): number | undefined {
  const digits = value.replace(/\D/g, '');
  return digits ? Number(digits) : undefined;
}

/** 사용 가능 포인트(balance)를 넘는 금액도 막는다. 잔액을 모르면 서버가 검사한다. (POINT_004) */
export function validateExchangeForm(values: ExchangeFormValues, balance?: number): ExchangeFormErrors {
  const errors: ExchangeFormErrors = {};
  const amount = amountOf(values.amount);
  if (!amount) errors.amount = '환전할 포인트를 입력해 주세요.';
  else if (amount < EXCHANGE_MIN || amount > EXCHANGE_MAX) errors.amount = '환전은 1,000 P 이상 1,000,000 P 이하로 신청할 수 있어요.';
  else if (balance !== undefined && amount > balance) errors.amount = '사용 가능 포인트보다 많이 환전할 수 없어요.';

  if (!/^\d{3}$/.test(values.bankCode)) errors.bankCode = '은행을 골라 주세요.';

  const account = values.accountNumber.trim();
  if (!account) errors.accountNumber = '계좌번호를 입력해 주세요.';
  else if (!/^[0-9-]{10,20}$/.test(account)) errors.accountNumber = '계좌번호는 숫자와 하이픈(-)으로 10~20자예요.';

  const holder = values.accountHolder.trim();
  if (!holder) errors.accountHolder = '예금주를 입력해 주세요.';
  else if (holder.length > ACCOUNT_HOLDER_MAX_LENGTH) errors.accountHolder = `예금주는 ${ACCOUNT_HOLDER_MAX_LENGTH}자 이하로 입력해 주세요.`;
  return errors;
}

export function toExchangeRequest(values: ExchangeFormValues): Schema<'PointExchangeRequest'> {
  return {
    amount: amountOf(values.amount) ?? 0,
    bankCode: values.bankCode,
    accountNumber: values.accountNumber.trim(),
    accountHolder: values.accountHolder.trim(),
  };
}

/** 은행 이름. 목록에 없는 코드면 코드를 그대로 보여 주지 않고 "은행"으로 */
export function bankName(code?: string): string {
  return (code && BANK_LABEL[code]) || '은행';
}

type ExchangeRow = Pick<Schema<'PointExchangeResponse'>, 'exchangeId' | 'status'>;

/** 서버가 아직 처리 중인 상태 (신청됨·송금 중) */
export function isExchangeInFlight(status?: string): boolean {
  return status === 'PENDING' || status === 'PROCESSING';
}

/**
 * 이전에 처리 중이던 신청 중 하나라도 끝났는지(송금 완료·실패·취소). 신청마다 이전 상태와 비교한다.
 * 끝났으면 잔액·포인트 내역이 바뀌었을 수 있다. (실패·취소면 포인트를 돌려받음)
 */
export function hasFinishedExchange(previous: ReadonlyMap<number, string | undefined>, rows: ExchangeRow[]): boolean {
  return rows.some((row) => row.exchangeId !== undefined && isExchangeInFlight(previous.get(row.exchangeId)) && !isExchangeInFlight(row.status));
}
