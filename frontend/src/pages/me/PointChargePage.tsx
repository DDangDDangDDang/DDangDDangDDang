import * as PortOne from '@portone/browser-sdk/v2';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api, call, errorMessage } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, buttonVariants, Card, CardBody, CardHeader, CardTitle, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { formatAmountInput, parseAmount } from '@/lib/auction';
import { cn } from '@/lib/cn';
import { formatNumber, formatPoint } from '@/lib/format';
import { CHARGE_PRESETS, chargeOutcomeOf, portOneConfig, validateChargeAmount, type ChargeOutcome, type PortOneConfig } from '@/lib/point-charge';

/** 결제창을 닫거나 결제가 실패해 충전을 멈춤 (포트원 결제창이 알려준 이유) */
type Stopped = { kind: 'stopped'; message: string };
type Result = ChargeOutcome | Stopped;

/**
 * 포인트 충전: 금액 고르기 → 충전 요청(POST /points/charges) → 포트원 결제창 → 결제 확인(POST /charges/complete)
 * 포인트는 서버가 포트원에 결제를 직접 조회해 확인한 뒤에만 지급한다. (화면이 보낸 금액은 믿지 않음)
 * 결제창이 이 화면으로 돌아오는 방식(redirectUrl)이면 주소의 paymentId로 결제를 확인한다.
 */
export function PointChargePage() {
  const queryClient = useQueryClient();
  const config = portOneConfig();
  const [amount, setAmount] = useState('');
  const [amountError, setAmountError] = useState<string>();
  const [result, setResult] = useState<Result>();

  const wallet = useQuery({
    queryKey: ['points', 'wallet'],
    queryFn: () => call(api.GET('/api/v1/points/wallet')),
  });

  const complete = async (paymentId: string): Promise<Result> => {
    const response = await call(api.POST('/api/v1/points/charges/complete', { body: { paymentId } }));
    // 잔액·포인트 내역을 새로 불러온다. (헤더의 잔액도 같이 바뀜)
    void queryClient.invalidateQueries({ queryKey: ['points'] });
    return chargeOutcomeOf(response);
  };

  const charge = useMutation({
    mutationFn: async ({ value, portOne }: { value: number; portOne: PortOneConfig }): Promise<Result> => {
      const request = await call(api.POST('/api/v1/points/charges', { body: { amount: value } }));
      if (!request?.paymentId) return { kind: 'failed', message: '충전 요청을 만들지 못했어요. 잠시 후 다시 시도해 주세요.' };
      const payment = await PortOne.requestPayment({
        storeId: portOne.storeId,
        channelKey: portOne.channelKey,
        paymentId: request.paymentId,
        orderName: request.orderName || `${formatNumber(value)} 포인트 충전`,
        totalAmount: value,
        currency: 'KRW',
        payMethod: 'CARD',
        // 결제사에 따라 결제창이 이 화면으로 돌아오며 끝날 수 있다.
        redirectUrl: `${window.location.origin}/points/charge`,
      });
      // code가 있으면 결제창을 닫았거나 결제가 실패한 것
      if (payment?.code !== undefined) return { kind: 'stopped', message: payment.message || '결제를 마치지 않아 충전하지 않았어요.' };
      return complete(request.paymentId);
    },
    onSuccess: (next) => {
      setResult(next);
      if (next.kind === 'done') setAmount('');
    },
    onError: () => setResult(undefined),
  });

  // 결제창이 이 화면으로 돌아온 경우: ?paymentId=...(&code=...&message=...)
  const [params, setParams] = useSearchParams();
  const returned = useRef(false);
  const confirm = useMutation({
    mutationFn: complete,
    onSuccess: setResult,
  });
  useEffect(() => {
    const paymentId = params.get('paymentId');
    if (!paymentId || returned.current) return;
    returned.current = true;
    const code = params.get('code');
    setParams({}, { replace: true });
    if (code) setResult({ kind: 'stopped', message: params.get('message') || '결제를 마치지 않아 충전하지 않았어요.' });
    else confirm.mutate(paymentId);
  }, [params, setParams, confirm]);

  const value = parseAmount(amount);
  const busy = charge.isPending || confirm.isPending;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const error = validateChargeAmount(amount);
    setAmountError(error);
    if (error || !value || !config) return;
    setResult(undefined);
    charge.mutate({ value, portOne: config });
  };

  return (
    <div className="mx-auto max-w-[640px]">
      <PageHeader
        title="포인트 충전"
        description="1원은 1포인트예요. 충전한 포인트로 경매에 입찰할 수 있어요."
        actions={
          <Link to="/points" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            포인트 내역
          </Link>
        }
      />

      <Card>
        <div className="flex items-center justify-between px-6 py-5">
          <span className="text-label text-neutral-500">사용 가능 포인트</span>
          {wallet.isPending ? (
            <Skeleton className="h-8 w-32" />
          ) : wallet.isError ? (
            <ErrorState message={errorMessage(wallet.error)} onRetry={() => void wallet.refetch()} />
          ) : (
            <span className="num text-price-lg font-bold">{formatPoint(wallet.data?.balance ?? 0)}</span>
          )}
        </div>
      </Card>

      <Card className="mt-4">
        <CardHeader>
          <CardTitle>충전할 금액</CardTitle>
        </CardHeader>
        <CardBody>
          <form onSubmit={onSubmit} noValidate aria-label="포인트 충전" className="flex flex-col gap-4">
            <div role="group" aria-label="금액 바로 고르기" className="grid grid-cols-4 gap-2">
              {CHARGE_PRESETS.map((preset) => (
                <button
                  key={preset}
                  type="button"
                  aria-pressed={value === preset}
                  disabled={busy}
                  onClick={() => {
                    setAmount(String(preset));
                    setAmountError(undefined);
                  }}
                  className={cn(
                    'num h-12 rounded-md border text-body font-semibold transition-colors disabled:opacity-50',
                    value === preset ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 hover:bg-neutral-25',
                  )}
                >
                  {formatNumber(preset)}원
                </button>
              ))}
            </div>
            <Input
              label="직접 입력"
              inputMode="numeric"
              suffix="원"
              placeholder="10,000"
              value={formatAmountInput(amount)}
              disabled={busy}
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

            {!config && (
              <Notice tone="error">
                결제 설정이 없어 충전할 수 없어요. frontend/.env.local에 VITE_PORTONE_STORE_ID와 VITE_PORTONE_CHANNEL_KEY를 적어 주세요.
              </Notice>
            )}
            {charge.isError && <Notice tone="error">{errorMessage(charge.error)}</Notice>}
            {confirm.isError && <Notice tone="error">결제 결과를 확인하지 못했어요. {errorMessage(confirm.error)}</Notice>}
            {confirm.isPending && <Notice>결제 결과를 확인하고 있어요.</Notice>}
            {result && <ResultNotice result={result} />}

            <Button type="submit" size="lg" loading={charge.isPending} disabled={!config || confirm.isPending}>
              {value ? `${formatNumber(value)}원 결제하기` : '결제하기'}
            </Button>
            <p className="text-caption text-neutral-500">결제는 포트원 결제창에서 카드로 진행돼요. 결제가 확인되면 바로 포인트가 들어와요.</p>
          </form>
        </CardBody>
      </Card>
    </div>
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
      return <Notice>결제를 확인하고 있어요. 확인되면 {formatPoint(result.amount)}가 자동으로 충전돼요.</Notice>;
    case 'failed':
      return <Notice tone="error">{result.message}</Notice>;
    case 'stopped':
      return <Notice tone="error">{result.message}</Notice>;
  }
}
