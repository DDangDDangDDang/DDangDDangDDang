import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Gift, Hourglass, Store } from 'lucide-react';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { buttonVariants, Card, CardBody, CardHeader, CardTitle, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { DONATION_HISTORY_INCREASES, DONATION_HISTORY_KEY, DONATION_HISTORY_LABEL, DONATION_WALLET_KEY } from '@/lib/donation';
import { formatDate, formatDateTime, formatMileage } from '@/lib/format';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';
import { useSellerProfile } from '@/lib/use-seller';

const PAGE_SIZE = 20;

/**
 * 도네이션 마일리지: 구매·판매 확정으로 적립하고 라이브 방송 선물·상점에 쓰는 마일리지 (현금 포인트와 따로, 환전할 수 없음)
 * - 내 마일리지(적립·선물받은 것 구분 없음)·곧 사라질 마일리지·이번 달 구매/판매 적립, 인증 판매자는 판매자 상점 바로가기
 * - 내역: 구매 적립·판매 적립·선물·선물받음·만료·상점 구매
 */
export function DonationPointsPage() {
  return (
    <div>
      <PageHeader
        title="도네이션 마일리지"
        description="구매·판매를 확정하거나 선물을 받으면 쌓이고, 라이브 방송 선물과 상점에 쓸 수 있어요. 현금으로 바꿀 수는 없어요."
      />
      <Summary />
      <History />
    </div>
  );
}

function Summary() {
  const session = useSession();
  const verifiedSeller = useSellerProfile(session?.user.userId).data?.verifiedSeller ?? false;
  const wallet = useQuery({
    queryKey: DONATION_WALLET_KEY,
    queryFn: () => call(api.GET('/api/v1/donation-points/wallet')),
  });

  if (wallet.isError) return <ErrorState message={errorMessage(wallet.error)} onRetry={() => void wallet.refetch()} />;
  const data = wallet.data;

  return (
    <div className="flex flex-col gap-3">
      <Card>
        <dl className="grid grid-cols-3 divide-x divide-neutral-100">
          <div className="flex flex-col gap-1 px-6 py-5">
            <dt className="flex items-center gap-1.5 text-label text-neutral-500">
              <Gift className="size-4" aria-hidden="true" />
              내 마일리지
            </dt>
            <dd className="num text-price-lg font-bold">{wallet.isPending ? <Skeleton className="h-8 w-32" /> : formatMileage(data?.balance ?? 0)}</dd>
            <dd className="text-caption text-neutral-500">
              <Link to="/lives" className="font-semibold text-brand-700 underline">
                라이브 방송 보러 가기
              </Link>
            </dd>
          </div>
          <div className="flex flex-col gap-1 px-6 py-5">
            <dt className="flex items-center gap-1.5 text-label text-neutral-500">
              <Hourglass className="size-4" aria-hidden="true" />곧 사라질 마일리지
            </dt>
            <dd className={cn('num text-title-md font-bold', (data?.expiringSoon ?? 0) > 0 && 'text-brand-700')}>
              {wallet.isPending ? <Skeleton className="h-7 w-24" /> : formatMileage(data?.expiringSoon ?? 0)}
            </dd>
            <dd className="text-caption text-neutral-500">
              {data?.nextExpiresAt ? `가장 먼저 사라지는 마일리지: ${formatDate(data.nextExpiresAt)}` : '사라질 마일리지가 없어요'}
            </dd>
          </div>
          <div className="flex flex-col gap-1 px-6 py-5">
            <dt className="text-label text-neutral-500">이번 달 적립</dt>
            {wallet.isPending ? (
              <dd>
                <Skeleton className="h-7 w-24" />
              </dd>
            ) : (
              <>
                <dd className="num text-body font-bold">구매 {`${formatMileage(data?.earnedThisMonth ?? 0)} / ${formatMileage(data?.monthlyEarnLimit ?? 0)}`}</dd>
                <dd className="num text-body font-bold">판매 {`${formatMileage(data?.saleEarnedThisMonth ?? 0)} / ${formatMileage(data?.monthlyEarnLimit ?? 0)}`}</dd>
              </>
            )}
            <dd className="text-caption text-neutral-500">구매·판매 적립은 한 달 한도가 따로 있어요.</dd>
          </div>
        </dl>
      </Card>

      {verifiedSeller && (
        <Card>
          <div className="flex items-center justify-between gap-4 px-6 py-5">
            <div className="flex flex-col gap-1">
              <span className="text-label font-semibold">판매자 상점</span>
              <span className="text-caption text-neutral-500">판매 적립과 시청자에게 받은 응원을 모아 파워 판매자 등을 살 수 있어요.</span>
            </div>
            <Link to="/me/donation-shop" className={buttonVariants({ variant: 'outline' })}>
              <Store className="size-4" aria-hidden="true" />
              판매자 상점
            </Link>
          </div>
        </Card>
      )}

      <Notice>
        구매·판매를 확정하면 낙찰가의 일부가, 선물을 받으면 받은 만큼이 쌓여요. 쌓인 날부터 유효기간이 지나면 사라지고, 먼저 사라지는 마일리지부터 쓰여요.
      </Notice>
    </div>
  );
}

function History() {
  const query = useInfiniteQuery({
    queryKey: DONATION_HISTORY_KEY,
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/donation-points/histories', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const rows = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <Card className="mt-4">
      <CardHeader>
        <CardTitle>도네이션 마일리지 내역</CardTitle>
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
          <EmptyState title="도네이션 마일리지 내역이 없어요" description="구매·판매를 확정하면 도네이션 마일리지가 쌓이고 여기에 기록돼요." />
        ) : (
          <table className="w-full text-body">
            <thead className="border-y border-neutral-100 bg-neutral-25 text-label text-neutral-500">
              <tr>
                <th scope="col" className="px-5 py-2.5 text-left font-semibold">
                  일시
                </th>
                <th scope="col" className="px-5 py-2.5 text-left font-semibold">
                  내용
                </th>
                <th scope="col" className="px-5 py-2.5 text-right font-semibold">
                  변동
                </th>
                <th scope="col" className="px-5 py-2.5 text-right font-semibold">
                  잔액
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => {
                const increases = row.type ? DONATION_HISTORY_INCREASES[row.type] : true;
                return (
                  <tr key={row.id} className="border-b border-neutral-100 last:border-b-0">
                    <td className="px-5 py-3 text-neutral-500">{row.createdAt ? formatDateTime(row.createdAt) : '-'}</td>
                    <td className="px-5 py-3 font-semibold">{row.type ? DONATION_HISTORY_LABEL[row.type] : '-'}</td>
                    <td className={cn('num px-5 py-3 text-right font-bold', increases ? 'text-info-700' : 'text-neutral-900')}>
                      {increases ? '+' : '−'}
                      {formatMileage(row.amount ?? 0)}
                    </td>
                    <td className="num px-5 py-3 text-right text-neutral-700">{formatMileage(row.balanceAfter ?? 0)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && <Skeleton className="m-5 h-12" />}
      </CardBody>
    </Card>
  );
}
