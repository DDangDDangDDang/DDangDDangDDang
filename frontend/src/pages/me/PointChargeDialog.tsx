import * as PortOne from '@portone/browser-sdk/v2';
import { useIsMutating, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage } from '@/api';
import { Button, Dialog, Input, Notice } from '@/components/ui';
import { formatAmountInput, parseAmount } from '@/lib/auction';
import { cn } from '@/lib/cn';
import { formatNumber, formatPoint } from '@/lib/format';
import { CHARGE_PRESETS, chargeOutcomeOf, portOneConfig, validateChargeAmount, type ChargeOutcome, type PortOneConfig } from '@/lib/point-charge';

/** 충전 요청. 결제창이 떠 있는 동안 창을 닫지 못하게 이름을 붙인다. */
const CHARGE_KEY = ['points', 'charge'];

/** 결제창을 닫거나 결제가 실패해 충전을 멈춤 (포트원 결제창이 알려준 이유) */
type Stopped = { kind: 'stopped'; message: string };
type Result = ChargeOutcome | Stopped;

/**
 * 포인트 충전 창 (포인트 화면의 [충전하기]). 1원 = 1포인트.
 * 충전 요청(POST /points/charges) → 포트원 결제창(카드, 팝업) → 결제 확인(POST /charges/complete)
 * 포인트는 서버가 포트원에 결제를 직접 조회해 금액이 맞을 때만 지급한다. 결제창이 code를 주면(취소·실패) 결제 확인을 부르지 않는다.
 * 결제창은 끝났는데 확인이 안 된 결제(PENDING이거나 확인 요청 실패)가 있으면 새 결제를 막고 그 결제의 확인만 다시 하게 한다. (중복 결제 방지)
 */
export function PointChargeDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const paying = useIsMutating({ mutationKey: CHARGE_KEY }) > 0;
  // 확인이 안 된 결제. 창을 닫았다 열어도 남도록 창 바깥(이 컴포넌트)에 둔다.
  const [unconfirmed, setUnconfirmed] = useState<string>();
  return (
    <Dialog open={open} onClose={onClose} closeDisabled={paying} title="포인트 충전" description="1원은 1포인트예요. 충전한 포인트로 경매에 입찰할 수 있어요." className="w-[520px]">
      {/* 닫았다 열면 이전 입력·결과 없이 새로 시작한다. */}
      {open && <ChargeForm unconfirmed={unconfirmed} onUnconfirmedChange={setUnconfirmed} />}
    </Dialog>
  );
}

function ChargeForm({ unconfirmed, onUnconfirmedChange }: { unconfirmed?: string; onUnconfirmedChange: (paymentId: string | undefined) => void }) {
  const queryClient = useQueryClient();
  const config = portOneConfig();
  const [amount, setAmount] = useState('');
  const [amountError, setAmountError] = useState<string>();
  const [result, setResult] = useState<Result>();

  const charge = useMutation({
    mutationKey: CHARGE_KEY,
    mutationFn: async ({ value, portOne }: { value: number; portOne: PortOneConfig }): Promise<Result> => {
      const request = await call(api.POST('/api/v1/points/charges', { body: { amount: value } }));
      if (!request?.paymentId) return { kind: 'failed', message: '충전 요청을 만들지 못했어요. 잠시 후 다시 시도해 주세요.' };
      const payment = await PortOne.requestPayment({
        storeId: portOne.storeId,
        channelKey: portOne.channelKey,
        paymentId: request.paymentId,
        orderName: request.orderName || `${formatNumber(value)} 포인트 충전`,
        totalAmount: request.amount ?? value,
        currency: 'KRW',
        payMethod: 'CARD',
        // SDK 필수값이라 지금 주소를 넣는다. PC 결제창은 팝업이라 결과는 Promise로 받는다.
        redirectUrl: window.location.href,
      });
      // code가 있으면 결제창을 닫았거나 결제가 실패한 것. 충전 요청은 서버에 PENDING으로 남아도 괜찮다.
      if (payment?.code !== undefined) return { kind: 'stopped', message: payment.message || '결제를 마치지 않아 충전하지 않았어요.' };

      // 여기부터는 결제가 됐을 수 있으므로 확인이 끝날 때까지 이 결제를 붙잡아 둔다.
      onUnconfirmedChange(request.paymentId);
      return confirmPayment(request.paymentId);
    },
    onSuccess: showResult,
    onError: () => setResult(undefined),
  });

  /** 결제 확인(POST /charges/complete). 서버는 여러 번 불러도 한 번만 지급한다. 지급됐거나 실패로 끝났을 때만 붙잡은 결제를 놓는다. */
  async function confirmPayment(paymentId: string): Promise<Result> {
    const completed = await call(api.POST('/api/v1/points/charges/complete', { body: { paymentId } }));
    const outcome = chargeOutcomeOf(completed);
    if (outcome.kind !== 'pending') onUnconfirmedChange(undefined);
    return outcome;
  }

  function showResult(next: Result) {
    setResult(next);
    if (next.kind === 'done') {
      setAmount('');
      // 잔액·포인트 내역을 새로 불러온다. (헤더의 잔액도 같이 바뀜)
      void queryClient.invalidateQueries({ queryKey: ['points'] });
    }
  }

  const recheck = useMutation({
    mutationKey: CHARGE_KEY,
    mutationFn: confirmPayment,
    onSuccess: showResult,
    onError: () => setResult(undefined),
  });

  const value = parseAmount(amount);

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!config || unconfirmed) return;
    const error = validateChargeAmount(amount);
    setAmountError(error);
    if (error || !value) return;
    setResult(undefined);
    charge.mutate({ value, portOne: config });
  };

  return (
    <form onSubmit={onSubmit} noValidate aria-label="포인트 충전" className="flex flex-col gap-4">
      <div role="group" aria-label="금액 바로 고르기" className="grid grid-cols-3 gap-2">
        {CHARGE_PRESETS.map((preset) => (
          <button
            key={preset}
            type="button"
            aria-pressed={value === preset}
            disabled={charge.isPending}
            onClick={() => {
              setAmount(String(preset));
              setAmountError(undefined);
            }}
            className={cn(
              'num h-11 rounded-md border text-body font-semibold transition-colors disabled:opacity-50',
              value === preset ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 hover:bg-neutral-25',
            )}
          >
            {formatNumber(preset)}원
          </button>
        ))}
      </div>
      <Input
        label="충전 금액"
        inputMode="numeric"
        suffix="원"
        placeholder="10,000"
        value={formatAmountInput(amount)}
        disabled={charge.isPending}
        error={amountError}
        hint="1,000원부터 1,000,000원까지 충전할 수 있어요."
        onChange={(e) => {
          setAmount(e.target.value);
          setAmountError(undefined);
        }}
      />
      <dl className="flex items-center justify-between rounded-md bg-neutral-25 px-4 py-3 text-body">
        <dt className="text-neutral-500">충전되는 포인트</dt>
        <dd className="num text-title-md font-bold">{formatPoint(value ?? 0)}</dd>
      </dl>

      {!config && <Notice tone="error">충전 설정이 필요합니다. 관리자에게 결제 설정(포트원 상점 ID·채널 키)을 요청해 주세요.</Notice>}
      {charge.isError && <Notice tone="error">{errorMessage(charge.error)}</Notice>}
      {recheck.isError && <Notice tone="error">결제 결과를 확인하지 못했어요. {errorMessage(recheck.error)}</Notice>}
      {result && <ResultNotice result={result} />}

      {unconfirmed ? (
        <>
          {!charge.isPending && <Notice>결제 결과를 아직 확인하지 못했어요. 결제가 두 번 되지 않도록 확인이 끝나야 새로 충전할 수 있어요.</Notice>}
          <Button type="button" size="lg" loading={charge.isPending || recheck.isPending} onClick={() => recheck.mutate(unconfirmed)}>
            결제 확인 다시 하기
          </Button>
        </>
      ) : (
        <Button type="submit" size="lg" loading={charge.isPending} disabled={!config}>
          {value ? `${formatNumber(value)}원 결제하기` : '결제하기'}
        </Button>
      )}
    </form>
  );
}

function ResultNotice({ result }: { result: Result }) {
  switch (result.kind) {
    case 'done':
      return (
        <Notice tone="success">
          {formatPoint(result.amount)}를 충전했어요.{result.balance !== undefined && ` 사용 가능 포인트는 ${formatPoint(result.balance)}예요.`}
        </Notice>
      );
    case 'pending':
      return <Notice>결제를 확인하고 있어요. 잠시 후 포인트 내역에서 확인해 주세요.</Notice>;
    case 'failed':
    case 'stopped':
      return <Notice tone="error">{result.message}</Notice>;
  }
}
