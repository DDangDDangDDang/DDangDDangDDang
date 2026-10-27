import { describe, expect, it } from 'vitest';
import { adminExchangeActions, bankName, EMPTY_EXCHANGE_FORM, hasFinishedExchange, toExchangeRequest, validateExchangeForm } from './exchange';

const valid = { amount: '50,000', bankCode: '004', accountNumber: '123-456-789012', accountHolder: ' 홍길동 ' };

describe('validateExchangeForm', () => {
  it('올바르면 오류가 없다', () => {
    expect(validateExchangeForm(valid, 100_000)).toEqual({});
  });

  it('빈 입력은 항목마다 안내한다', () => {
    expect(Object.keys(validateExchangeForm(EMPTY_EXCHANGE_FORM))).toEqual(['amount', 'bankCode', 'accountNumber', 'accountHolder']);
  });

  it('금액은 1,000~1,000,000 P, 사용 가능 포인트 이하', () => {
    expect(validateExchangeForm({ ...valid, amount: '999' }).amount).toContain('1,000 P 이상');
    expect(validateExchangeForm({ ...valid, amount: '1000001' }).amount).toContain('1,000,000 P 이하');
    expect(validateExchangeForm({ ...valid, amount: '60000' }, 50_000).amount).toBe('사용 가능 포인트보다 많이 환전할 수 없어요.');
  });

  it('계좌번호는 숫자·하이픈 10~20자', () => {
    expect(validateExchangeForm({ ...valid, accountNumber: '12345' }).accountNumber).toBeDefined();
    expect(validateExchangeForm({ ...valid, accountNumber: '1234-5678-ab' }).accountNumber).toBeDefined();
    expect(validateExchangeForm({ ...valid, accountNumber: '----------' }).accountNumber).toBeDefined();
  });
});

describe('toExchangeRequest', () => {
  it('금액은 숫자로, 앞뒤 공백은 뺀다', () => {
    expect(toExchangeRequest(valid)).toEqual({ amount: 50000, bankCode: '004', accountNumber: '123-456-789012', accountHolder: '홍길동' });
  });
});

describe('bankName', () => {
  it('코드를 은행 이름으로', () => {
    expect(bankName('088')).toBe('신한은행');
    expect(bankName('999')).toBe('은행');
  });
});

describe('hasFinishedExchange', () => {
  const previous = new Map<number, string | undefined>([
    [1, 'PENDING'],
    [2, 'PROCESSING'],
    [3, 'COMPLETE'],
  ]);

  it('처리 중이던 신청이 하나라도 끝나면 true', () => {
    expect(hasFinishedExchange(previous, [{ exchangeId: 1, status: 'PENDING' }, { exchangeId: 2, status: 'FAILED' }])).toBe(true);
  });

  it('여전히 처리 중이거나, 처음 보는 신청이거나, 원래 끝난 신청이면 false', () => {
    expect(hasFinishedExchange(previous, [{ exchangeId: 1, status: 'PROCESSING' }, { exchangeId: 3, status: 'COMPLETE' }, { exchangeId: 9, status: 'FAILED' }])).toBe(false);
  });
});

describe('adminExchangeActions', () => {
  it('신청됨은 송금 시작·반려, 송금 중은 송금 완료·반려', () => {
    expect(adminExchangeActions('PENDING')).toEqual(['reject', 'start']);
    expect(adminExchangeActions('PROCESSING')).toEqual(['reject', 'complete']);
  });

  it('끝난 신청은 할 일이 없다', () => {
    for (const status of ['COMPLETE', 'FAILED', 'CANCELLED', undefined]) expect(adminExchangeActions(status)).toEqual([]);
  });
});
