import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { api, call, errorMessage, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { buttonVariants, Card, CardBody, CardHeader, CardTitle, EmptyState, ErrorState, Select, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatDateTime, formatPoint } from '@/lib/format';
import { POINT_HISTORY_INCREASES, POINT_HISTORY_LABEL, type PointHistoryType } from '@/lib/labels';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';
import { ExchangeSection } from './ExchangeSection';

const PAGE_SIZE = 20;
const TYPE_OPTIONS = [
  { value: '', label: '전체' },
  ...(Object.entries(POINT_HISTORY_LABEL) as [PointHistoryType, string][]).map(([value, label]) => ({ value, label })),
];

/** 포인트: 잔액(사용 가능·입찰로 묶임·합계) + [충전하기] + 환전 신청·내역 + 포인트 내역(스크롤하면 계속 불러오기, 종류 필터) */
export function PointsPage() {
  return (
    <div className="mx-auto max-w-[880px]">
      <PageHeader
        title="포인트"
        actions={
          <Link to="/points/charge" className={buttonVariants({ size: 'sm' })}>
            충전하기
          </Link>
        }
      />
      <WalletSummary />
      <ExchangeSection />
      <PointHistory />
    </div>
  );
}

function WalletSummary() {
  const query = useQuery({
    queryKey: ['points', 'wallet'],
    queryFn: () => call(api.GET('/api/v1/points/wallet')),
  });

  if (query.isError) {
    return (
      <Card>
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      </Card>
    );
  }

  const items = [
    { label: '사용 가능 포인트', value: query.data?.balance, emphasis: true },
    { label: '입찰로 묶인 포인트', value: query.data?.heldBalance },
    { label: '합계', value: query.data?.totalBalance },
  ];

  return (
    <Card>
      <dl className="grid grid-cols-3 divide-x divide-neutral-100">
        {items.map((item) => (
          <div key={item.label} className="flex flex-col gap-1 px-6 py-5">
            <dt className="text-label text-neutral-500">{item.label}</dt>
            <dd className={cn('num font-bold', item.emphasis ? 'text-price-lg' : 'text-title-md')}>
              {query.isPending ? <Skeleton className="h-8 w-32" /> : formatPoint(item.value ?? 0)}
            </dd>
          </div>
        ))}
      </dl>
    </Card>
  );
}

function PointHistory() {
  const [params, setParams] = useSearchParams();
  const type = (params.get('type') || undefined) as PointHistoryType | undefined;

  const query = useInfiniteQuery({
    queryKey: ['points', 'histories', type ?? 'ALL'],
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/points/histories', { params: { query: { type, cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });

  const sentinelRef = useInfiniteScroll(query.hasNextPage, query.isFetchingNextPage, query.fetchNextPage);
  const rows = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>포인트 내역</CardTitle>
        <div className="w-[180px]">
          <Select
            label="종류"
            hideLabel
            options={TYPE_OPTIONS}
            value={type ?? ''}
            onChange={(e) => setParams(e.target.value ? { type: e.target.value } : {}, { replace: true })}
          />
        </div>
      </CardHeader>
      <CardBody className="px-0 pb-0">
        {query.isPending ? (
          <div className="flex flex-col gap-2 px-5 pb-5">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-12" />
            ))}
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : rows.length === 0 ? (
          <EmptyState title="포인트 내역이 없어요" description="충전하거나 입찰하면 여기에 기록돼요." />
        ) : (
          <table className="w-full text-body">
            <thead className="border-y border-neutral-100 bg-neutral-25 text-label text-neutral-500">
              <tr>
                <th scope="col" className="px-5 py-2.5 text-left font-semibold">날짜</th>
                <th scope="col" className="px-5 py-2.5 text-left font-semibold">종류</th>
                <th scope="col" className="px-5 py-2.5 text-right font-semibold">금액</th>
                <th scope="col" className="px-5 py-2.5 text-right font-semibold">변동 후 잔액</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <HistoryRow key={row.historyId} row={row} />
              ))}
            </tbody>
          </table>
        )}
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && (
          <div className="px-5 py-4">
            <Skeleton className="h-12" />
          </div>
        )}
      </CardBody>
    </Card>
  );
}

function HistoryRow({ row }: { row: Schema<'PointHistoryResponse'> }) {
  const increases = row.type ? POINT_HISTORY_INCREASES[row.type] : true;
  const amount = Math.abs(row.amount ?? 0);
  return (
    <tr className="border-b border-neutral-100 last:border-b-0">
      <td className="px-5 py-3.5 text-neutral-500">{row.createdAt ? formatDateTime(row.createdAt) : '-'}</td>
      <td className="px-5 py-3.5">{row.type ? POINT_HISTORY_LABEL[row.type] : '-'}</td>
      <td className={cn('num px-5 py-3.5 text-right font-bold', increases ? 'text-info-700' : 'text-neutral-900')}>
        {increases ? '+' : '−'}
        {formatPoint(amount)}
      </td>
      <td className="num px-5 py-3.5 text-right">{formatPoint(row.balanceAfter ?? 0)}</td>
    </tr>
  );
}
