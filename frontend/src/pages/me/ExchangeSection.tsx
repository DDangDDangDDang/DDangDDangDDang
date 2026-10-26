import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, call, errorMessage, isErrorCode, type Schema } from '@/api';
import { Badge, Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, EmptyState, ErrorState, Input, Notice, Select, Skeleton, type BadgeProps } from '@/components/ui';
import {
  bankName,
  EMPTY_EXCHANGE_FORM,
  EXCHANGE_MAX,
  EXCHANGE_MIN,
  hasFinishedExchange,
  isExchangeInFlight,
  toExchangeRequest,
  validateExchangeForm,
  type ExchangeFormErrors,
  type ExchangeFormValues,
} from '@/lib/exchange';
import { formatDateTime, formatNumber, formatPoint } from '@/lib/format';
import { BANK_LABEL, EXCHANGE_STATUS_LABEL, type ExchangeStatus } from '@/lib/labels';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Exchange = Schema<'PointExchangeResponse'>;

const PAGE_SIZE = 10;
/** 신청됨·송금 중인 신청이 있으면 이 간격으로 다시 불러온다. (송금은 관리자가 확인한 뒤 처리한다) */
const REFRESH_MS = 10_000;
const BANK_OPTIONS = [{ value: '', label: '은행 선택' }, ...Object.entries(BANK_LABEL).map(([value, label]) => ({ value, label }))];
const STATUS_BADGE: Record<ExchangeStatus, NonNullable<BadgeProps['variant']>> = {
  PENDING: 'outline',
  PROCESSING: 'info',
  COMPLETE: 'neutral',
  FAILED: 'warning',
  CANCELLED: 'neutral',
};

/**
 * 포인트 환전: 신청(금액·은행·계좌·예금주) + 신청 내역(취소).
 * 신청하면 사용 가능 포인트에서 바로 빠지고, 관리자가 확인한 뒤 송금한다. 신청됨(PENDING)일 때만 취소할 수 있고, 취소·반려면 포인트를 돌려받는다.
 */
export function ExchangeSection() {
  // 잔액 요약과 같은 캐시를 쓴다.
  const wallet = useQuery({ queryKey: ['points', 'wallet'], queryFn: () => call(api.GET('/api/v1/points/wallet')) });
  const balance = wallet.data?.balance;
  const [open, setOpen] = useState(false);
  const [done, setDone] = useState<string>();

  return (
    <Card className="mt-4" aria-labelledby="exchange-title">
      <CardHeader>
        <div className="flex flex-col gap-0.5">
          <CardTitle id="exchange-title">포인트 환전</CardTitle>
          <span className="text-caption text-neutral-500">
            판매 정산 등으로 받은 포인트를 내 계좌로 보내요. 한 번에 {formatNumber(EXCHANGE_MIN)}~{formatNumber(EXCHANGE_MAX)} P
          </span>
        </div>
        {!open && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              setOpen(true);
              setDone(undefined);
            }}
          >
            환전 신청
          </Button>
        )}
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        {done && <Notice tone="success">{done}</Notice>}
        {open && (
          <ExchangeForm
            balance={balance}
            onClose={() => setOpen(false)}
            onDone={(message) => {
              setOpen(false);
              setDone(message);
            }}
          />
        )}
        <ExchangeList />
      </CardBody>
    </Card>
  );
}

function ExchangeForm({ balance, onClose, onDone }: { balance?: number; onClose: () => void; onDone: (message: string) => void }) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState<ExchangeFormValues>(EMPTY_EXCHANGE_FORM);
  const [errors, setErrors] = useState<ExchangeFormErrors>({});
  const [confirming, setConfirming] = useState(false);
  const request = toExchangeRequest(values);

  const mutation = useMutation({
    mutationFn: () => call(api.POST('/api/v1/points/exchanges', { body: request })),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['points'] });
      onDone(`${formatPoint(request.amount)} 환전을 신청했어요. 관리자가 확인하고 송금하면 내역이 "송금 완료"로 바뀌어요.`);
    },
    onError: () => setConfirming(false),
  });

  const update = (patch: Partial<ExchangeFormValues>) => {
    setValues((prev) => ({ ...prev, ...patch }));
    setErrors((prev) => ({ ...prev, ...Object.fromEntries(Object.keys(patch).map((k) => [k, undefined])) }));
  };

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const next = validateExchangeForm(values, balance);
    setErrors(next);
    if (Object.keys(next).length === 0) setConfirming(true);
  };

  return (
    <form onSubmit={onSubmit} noValidate aria-label="환전 신청" className="flex flex-col gap-4 rounded-md bg-neutral-25 p-4">
      <div className="grid grid-cols-2 gap-4">
        <Input
          label="환전할 포인트"
          inputMode="numeric"
          suffix="P"
          placeholder="10,000"
          value={values.amount ? formatNumber(Number(values.amount.replace(/\D/g, '')) || 0) : ''}
          onChange={(e) => update({ amount: e.target.value.replace(/\D/g, '') })}
          error={errors.amount}
          hint={balance !== undefined ? `사용 가능 ${formatPoint(balance)}` : undefined}
        />
        <Select label="은행" options={BANK_OPTIONS} value={values.bankCode} onChange={(e) => update({ bankCode: e.target.value })} error={errors.bankCode} />
        <Input
          label="계좌번호"
          inputMode="numeric"
          placeholder="숫자와 하이픈(-)만"
          maxLength={20}
          autoComplete="off"
          value={values.accountNumber}
          onChange={(e) => update({ accountNumber: e.target.value.replace(/[^0-9-]/g, '') })}
          error={errors.accountNumber}
        />
        <Input label="예금주" maxLength={30} autoComplete="off" value={values.accountHolder} onChange={(e) => update({ accountHolder: e.target.value })} error={errors.accountHolder} />
      </div>
      <p className="text-caption text-neutral-500">신청하면 사용 가능 포인트에서 바로 빠지고, 관리자가 확인한 뒤 계좌로 보내 드려요. 송금 전(신청됨)에는 취소할 수 있고, 취소하거나 반려되면 돌려받아요.</p>
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onClose}>
          닫기
        </Button>
        <Button type="submit">환전 신청</Button>
      </div>

      <ConfirmDialog
        open={confirming}
        title="환전을 신청할까요?"
        description={
          <dl className="grid grid-cols-[80px_1fr] gap-y-1">
            <dt className="text-neutral-500">금액</dt>
            <dd className="num font-semibold">{formatPoint(request.amount)}</dd>
            <dt className="text-neutral-500">받는 계좌</dt>
            <dd>
              {bankName(request.bankCode)} {request.accountNumber}
            </dd>
            <dt className="text-neutral-500">예금주</dt>
            <dd>{request.accountHolder}</dd>
          </dl>
        }
        confirmLabel="신청"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate()}
        onCancel={() => setConfirming(false)}
      />
    </form>
  );
}

function ExchangeList() {
  const queryClient = useQueryClient();
  const [canceling, setCanceling] = useState<Exchange>();
  const query = useInfiniteQuery({
    queryKey: ['points', 'exchanges'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/points/exchanges', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
    refetchInterval: (q) => (q.state.data?.pages.some((page) => page?.content?.some((row) => isExchangeInFlight(row.status))) ? REFRESH_MS : false),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage, query.isFetchingNextPage, query.fetchNextPage);
  const rows = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  // 처리 중이던 신청이 끝나면(송금 완료·실패) 잔액·포인트 내역도 바뀌었으니 다시 불러온다. 신청마다 이전 상태와 비교한다.
  const previousStatusRef = useRef(new Map<number, string | undefined>());
  useEffect(() => {
    if (!query.data) return;
    const current = query.data.pages.flatMap((page) => page?.content ?? []);
    if (hasFinishedExchange(previousStatusRef.current, current)) {
      void queryClient.invalidateQueries({ queryKey: ['points', 'wallet'] });
      void queryClient.invalidateQueries({ queryKey: ['points', 'histories'] });
    }
    previousStatusRef.current = new Map(current.filter((row) => row.exchangeId !== undefined).map((row) => [row.exchangeId as number, row.status]));
  }, [query.data, queryClient]);

  const cancel = useMutation({
    mutationFn: (exchangeId: number) => call(api.POST('/api/v1/points/exchanges/{exchangeId}/cancel', { params: { path: { exchangeId } } })),
    onSuccess: () => {
      setCanceling(undefined);
      void queryClient.invalidateQueries({ queryKey: ['points'] });
    },
    // 그 사이 송금이 시작됐으면 상태가 바뀌었으니 다시 불러온다.
    onError: (error) => {
      if (isErrorCode(error, 'POINT_043')) void queryClient.invalidateQueries({ queryKey: ['points', 'exchanges'] });
    },
  });

  return (
    <section aria-label="환전 신청 내역">
      <h3 className="mb-2 text-body font-semibold">신청 내역</h3>
      {query.isPending ? (
        <div className="flex flex-col gap-2">
          <Skeleton className="h-14" />
          <Skeleton className="h-14" />
        </div>
      ) : query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title="환전 신청 내역이 없어요" description="[환전 신청]으로 포인트를 내 계좌로 보낼 수 있어요." />
      ) : (
        <ul className="divide-y divide-neutral-100 rounded-md border border-neutral-100">
          {rows.map((row) => (
            <li key={row.exchangeId} className="flex items-center gap-4 px-4 py-3">
              {row.status && <Badge variant={STATUS_BADGE[row.status]}>{EXCHANGE_STATUS_LABEL[row.status]}</Badge>}
              <div className="flex min-w-0 flex-1 flex-col">
                <span className="num text-body-lg font-bold">{formatPoint(row.amount ?? 0)}</span>
                <span className="truncate text-caption text-neutral-500">
                  {bankName(row.bankCode)} {row.accountNumber} · {row.accountHolder}
                  {row.createdAt ? ` · ${formatDateTime(row.createdAt)} 신청` : ''}
                </span>
                {row.status === 'FAILED' && <span className="text-caption text-brand-700">{row.failReason || '송금하지 못했어요.'} 포인트는 돌려받았어요.</span>}
              </div>
              {row.status === 'PENDING' && (
                <Button variant="outline" size="sm" onClick={() => setCanceling(row)}>
                  취소
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && <Skeleton className="mt-2 h-14" />}

      <ConfirmDialog
        open={canceling !== undefined}
        title="환전 신청을 취소할까요?"
        description={
          <div className="flex flex-col gap-2">
            <p>{formatPoint(canceling?.amount ?? 0)}를 사용 가능 포인트로 돌려받아요.</p>
            {cancel.isError && (
              <Notice tone="error">
                {isErrorCode(cancel.error, 'POINT_043') ? '이미 송금을 시작해 취소할 수 없어요. 내역을 새로 불러왔어요.' : errorMessage(cancel.error)}
              </Notice>
            )}
          </div>
        }
        confirmLabel="신청 취소"
        cancelLabel="닫기"
        loading={cancel.isPending}
        onConfirm={() => canceling?.exchangeId && cancel.mutate(canceling.exchangeId)}
        onCancel={() => {
          setCanceling(undefined);
          cancel.reset();
        }}
      />
    </section>
  );
}
