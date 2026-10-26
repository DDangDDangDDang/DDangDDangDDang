import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Check, Copy } from 'lucide-react';
import { useState } from 'react';
import { api, call, errorMessage, isErrorCode, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, ConfirmDialog, Dialog, EmptyState, ErrorState, Notice, Select, Skeleton, Textarea, type BadgeProps } from '@/components/ui';
import { adminExchangeActions, bankName } from '@/lib/exchange';
import { formatDateTime, formatPoint } from '@/lib/format';
import { EXCHANGE_STATUS_LABEL, type ExchangeStatus } from '@/lib/labels';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Exchange = Schema<'AdminPointExchangeResponse'>;

const PAGE_SIZE = 20;
const QUERY_KEY = ['admin', 'point-exchanges'] as const;
const STATUS_OPTIONS = [
  { value: 'PENDING', label: '송금할 신청' },
  { value: 'PROCESSING', label: '송금 중' },
  { value: 'COMPLETE', label: '송금 완료' },
  { value: 'FAILED', label: '반려' },
  { value: 'CANCELLED', label: '회원이 취소' },
  { value: '', label: '전체' },
];
const STATUS_BADGE: Record<ExchangeStatus, NonNullable<BadgeProps['variant']>> = {
  PENDING: 'outline',
  PROCESSING: 'info',
  COMPLETE: 'neutral',
  FAILED: 'warning',
  CANCELLED: 'neutral',
};
/** 다른 관리자가 먼저 처리했거나 회원이 취소해서 상태가 바뀐 경우 */
const STATUS_CHANGED = 'POINT_043' as const;

/**
 * 환전 승인 (관리자): 회원의 환전 신청을 확인하고 직접 이체한 뒤 완료 처리한다.
 * 송금 시작 → (은행 앱으로 이체) → 송금 완료. 문제가 있으면 사유를 적어 반려하고, 포인트는 회원에게 돌아간다.
 * 송금 시작을 먼저 눌러야 그 사이 회원이 신청을 취소하지 못한다.
 */
export function PointExchangesAdminPage() {
  const session = useSession();
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<ExchangeStatus | ''>('PENDING');
  const [starting, setStarting] = useState<Exchange>();
  const [completing, setCompleting] = useState<Exchange>();
  const [rejecting, setRejecting] = useState<Exchange>();
  const [done, setDone] = useState<string>();

  const query = useInfiniteQuery({
    queryKey: [...QUERY_KEY, status],
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/admin/point-exchanges', { params: { query: { status: status || undefined, cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
    enabled: session?.user.role === 'ADMIN',
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const exchanges = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  const refresh = () => void queryClient.invalidateQueries({ queryKey: QUERY_KEY });

  const start = useMutation({
    mutationFn: (id: number) => call(api.POST('/api/v1/admin/point-exchanges/{exchangeId}/start', { params: { path: { exchangeId: id } } })),
    onSuccess: () => {
      setDone(`${starting?.accountHolder ?? ''}님 계좌로 이체할 차례예요. 이체를 마치면 [송금 완료]를 눌러 주세요.`);
      setStarting(undefined);
    },
    onSettled: refresh,
  });
  const complete = useMutation({
    mutationFn: (id: number) => call(api.POST('/api/v1/admin/point-exchanges/{exchangeId}/complete', { params: { path: { exchangeId: id } } })),
    onSuccess: () => {
      setDone(`${formatPoint(completing?.amount ?? 0)} 송금을 완료로 처리했어요.`);
      setCompleting(undefined);
    },
    onSettled: refresh,
  });

  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="환전 신청은 관리자가 확인하고 송금해요." />;
  }

  return (
    <div className="mx-auto max-w-[960px]">
      <PageHeader title="환전 승인" description="회원이 신청한 환전을 확인하고, 회사 계좌에서 직접 이체한 뒤 완료로 처리해요." />
      <Notice className="mb-4">
        이체하기 전에 꼭 [송금 시작]을 먼저 눌러 주세요. 시작한 뒤에는 회원이 신청을 취소할 수 없어 같은 돈이 두 번 나가지 않아요.
      </Notice>
      <div className="mb-4 w-[220px]">
        <Select label="보기" hideLabel options={STATUS_OPTIONS} value={status} onChange={(e) => setStatus(e.target.value as ExchangeStatus | '')} />
      </div>
      {done && (
        <Notice tone="success" className="mb-3">
          {done}
        </Notice>
      )}
      <Card aria-label="환전 신청 목록">
        {query.isPending ? (
          <div className="flex flex-col gap-2 p-4">
            <Skeleton className="h-24" />
            <Skeleton className="h-24" />
          </div>
        ) : query.isError && exchanges.length === 0 ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : exchanges.length === 0 ? (
          <EmptyState
            title={status === 'PENDING' ? '송금할 신청이 없어요' : '신청이 없어요'}
            description="회원이 [포인트 → 포인트 환전]에서 신청하면 여기에 나와요."
          />
        ) : (
          <ul className="divide-y divide-neutral-100">
            {exchanges.map((exchange) => (
              <ExchangeRow
                key={exchange.exchangeId}
                exchange={exchange}
                onStart={() => {
                  start.reset();
                  setDone(undefined);
                  setStarting(exchange);
                }}
                onComplete={() => {
                  complete.reset();
                  setDone(undefined);
                  setCompleting(exchange);
                }}
                onReject={() => {
                  setDone(undefined);
                  setRejecting(exchange);
                }}
              />
            ))}
          </ul>
        )}
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && <Skeleton className="m-4 h-24" />}
      </Card>

      <ConfirmDialog
        open={starting !== undefined}
        title="송금을 시작할까요?"
        description={
          <div className="flex flex-col gap-3">
            <TransferSummary exchange={starting} />
            <p>시작하면 회원이 신청을 취소할 수 없어요. 그다음 은행 앱에서 위 계좌로 이체해 주세요.</p>
            {start.isError && <Notice tone="error">{actionError(start.error)}</Notice>}
          </div>
        }
        confirmLabel="송금 시작"
        cancelLabel="닫기"
        loading={start.isPending}
        onConfirm={() => starting?.exchangeId && start.mutate(starting.exchangeId)}
        onCancel={() => setStarting(undefined)}
      />
      <ConfirmDialog
        open={completing !== undefined}
        title="이체를 마쳤나요?"
        description={
          <div className="flex flex-col gap-3">
            <TransferSummary exchange={completing} />
            <p>은행 앱에서 이체가 끝난 것을 확인한 뒤 완료해 주세요. 완료하면 되돌릴 수 없어요.</p>
            {complete.isError && <Notice tone="error">{actionError(complete.error)}</Notice>}
          </div>
        }
        confirmLabel="송금 완료"
        cancelLabel="닫기"
        loading={complete.isPending}
        onConfirm={() => completing?.exchangeId && complete.mutate(completing.exchangeId)}
        onCancel={() => setCompleting(undefined)}
      />
      {/* 신청마다 새로 그려 이전 신청의 반려 사유·오류가 남지 않게 한다. */}
      <RejectDialog
        key={rejecting?.exchangeId ?? 'none'}
        exchange={rejecting}
        onClose={() => setRejecting(undefined)}
        onRejected={(message) => {
          setDone(message);
          refresh();
        }}
        onStatusChanged={refresh}
      />
    </div>
  );
}

function ExchangeRow({ exchange, onStart, onComplete, onReject }: { exchange: Exchange; onStart: () => void; onComplete: () => void; onReject: () => void }) {
  const actions = adminExchangeActions(exchange.status);

  return (
    <li className="flex items-start justify-between gap-6 px-5 py-4">
      <div className="flex min-w-0 flex-col gap-1.5">
        <span className="flex items-center gap-2">
          {exchange.status && <Badge variant={STATUS_BADGE[exchange.status]}>{EXCHANGE_STATUS_LABEL[exchange.status]}</Badge>}
          <strong className="num text-body-lg font-bold">{formatPoint(exchange.amount ?? 0)}</strong>
          <span className="text-label text-neutral-500">
            {exchange.nickname || '알 수 없는 회원'} · {exchange.email || '-'}
          </span>
        </span>
        <span className="flex items-center gap-2 text-body">
          <span className="font-semibold">{bankName(exchange.bankCode)}</span>
          <span className="num">{exchange.accountNumber || '-'}</span>
          {exchange.accountNumber && <CopyButton value={exchange.accountNumber} label="계좌번호 복사" />}
          <span className="text-neutral-700">예금주 {exchange.accountHolder || '-'}</span>
        </span>
        <span className="text-caption text-neutral-500">
          {exchange.createdAt ? `${formatDateTime(exchange.createdAt)} 신청` : ''}
          {exchange.processingStartedAt ? ` · ${formatDateTime(exchange.processingStartedAt)} 송금 시작` : ''}
          {exchange.status === 'COMPLETE' && exchange.updatedAt ? ` · ${formatDateTime(exchange.updatedAt)} 완료` : ''}
        </span>
        {exchange.status === 'FAILED' && exchange.failReason && <span className="text-caption text-brand-700">반려 사유: {exchange.failReason}</span>}
      </div>
      {actions.length > 0 && (
        <div className="flex shrink-0 gap-2">
          {actions.includes('reject') && (
            <Button variant="outline" size="sm" onClick={onReject}>
              반려
            </Button>
          )}
          {actions.includes('start') && (
            <Button size="sm" onClick={onStart}>
              송금 시작
            </Button>
          )}
          {actions.includes('complete') && (
            <Button size="sm" variant="secondary" onClick={onComplete}>
              송금 완료
            </Button>
          )}
        </div>
      )}
    </li>
  );
}

/** 이체할 내용 요약 (확인 창 안) */
function TransferSummary({ exchange }: { exchange?: Exchange }) {
  return (
    <dl className="grid grid-cols-[80px_1fr] gap-y-1">
      <dt className="text-neutral-500">금액</dt>
      <dd className="num font-semibold">{formatPoint(exchange?.amount ?? 0)}</dd>
      <dt className="text-neutral-500">받는 계좌</dt>
      <dd>
        {bankName(exchange?.bankCode)} <span className="num">{exchange?.accountNumber}</span>
      </dd>
      <dt className="text-neutral-500">예금주</dt>
      <dd>{exchange?.accountHolder}</dd>
    </dl>
  );
}

function CopyButton({ value, label }: { value: string; label: string }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // 복사가 막힌 브라우저면 화면의 번호를 직접 보고 입력한다.
    }
  };

  return (
    <button
      type="button"
      onClick={() => void copy()}
      aria-label={copied ? '복사했어요' : label}
      title={copied ? '복사했어요' : label}
      className="inline-flex size-7 items-center justify-center rounded-md text-neutral-500 hover:bg-neutral-50 hover:text-neutral-900"
    >
      {copied ? <Check className="size-4" aria-hidden="true" /> : <Copy className="size-4" aria-hidden="true" />}
    </button>
  );
}

function RejectDialog({
  exchange,
  onClose,
  onRejected,
  onStatusChanged,
}: {
  exchange?: Exchange;
  onClose: () => void;
  onRejected: (message: string) => void;
  onStatusChanged: () => void;
}) {
  const [reason, setReason] = useState('');
  const reject = useMutation({
    mutationFn: () =>
      call(api.POST('/api/v1/admin/point-exchanges/{exchangeId}/reject', { params: { path: { exchangeId: exchange?.exchangeId ?? 0 } }, body: { reason: reason.trim() } })),
    onSuccess: () => {
      onRejected(`${exchange?.nickname || '회원'}님의 환전 신청을 반려했어요. ${formatPoint(exchange?.amount ?? 0)}는 회원에게 돌아갔어요.`);
      onClose();
    },
    onError: (error) => {
      if (isErrorCode(error, STATUS_CHANGED)) onStatusChanged();
    },
  });

  return (
    <Dialog
      open={exchange !== undefined}
      title="환전 반려"
      description={`${formatPoint(exchange?.amount ?? 0)} 신청을 반려하고 포인트를 회원에게 돌려줘요. 사유는 회원에게 보여요.`}
      onClose={onClose}
      closeDisabled={reject.isPending}
      className="w-[520px]"
    >
      <div className="flex flex-col gap-3">
        {exchange?.status === 'PROCESSING' && <Notice tone="error">이미 이체했다면 반려하지 마세요. 돈과 포인트가 모두 회원에게 가요.</Notice>}
        <Textarea label="반려 사유" placeholder="예) 예금주와 회원 정보가 일치하지 않아요." maxLength={200} rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
        {reject.isError && <Notice tone="error">{actionError(reject.error)}</Notice>}
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose} disabled={reject.isPending}>
            닫기
          </Button>
          <Button disabled={!reason.trim()} loading={reject.isPending} onClick={() => reject.mutate()}>
            반려하기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

function actionError(error: unknown): string {
  return isErrorCode(error, STATUS_CHANGED) ? '그 사이 신청 상태가 바뀌었어요. 목록을 새로 불러왔으니 다시 확인해 주세요.' : errorMessage(error);
}
