import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { ChevronRight } from 'lucide-react';
import { Link, useSearchParams } from 'react-router';
import { api, call, errorMessage, useSession } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { OrderStatusBadge, ProductImage } from '@/components/order/OrderBits';
import { Button, Card, EmptyState, ErrorState, Notice, Select, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatDate, formatPoint } from '@/lib/format';
import { ORDER_STATUS_LABEL, type OrderStatus } from '@/lib/labels';
import type { OrderRole } from '@/lib/order-view';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';
import { useSellerProfile } from '@/lib/use-seller';

const PAGE_SIZE = 20;
const TABS: { role: OrderRole; label: string }[] = [
  { role: 'BUYER', label: '구매 내역' },
  { role: 'SELLER', label: '판매 내역' },
];
const STATUS_OPTIONS = [
  { value: '', label: '전체 상태' },
  ...(Object.entries(ORDER_STATUS_LABEL) as [OrderStatus, string][]).map(([value, label]) => ({ value, label })),
];

/**
 * 주문 목록: [구매 내역] / [판매 내역] 탭, 상태 필터, 스크롤하면 계속 불러오기.
 * 주문은 낙찰되면 자동으로 생기므로 "주문하기" 화면은 없다.
 */
export function OrdersPage() {
  const [params, setParams] = useSearchParams();
  // [판매 내역]은 판매자 인증을 받은 회원에게만 보인다.
  // 인증 전이라도 이미 판매한 주문이 있으면(인증 제도 전 거래) 발송·확정을 끝낼 수 있게 보여 준다.
  const session = useSession();
  const profile = useSellerProfile(session?.user.userId);
  const notVerified = profile.data !== undefined && !profile.data.verifiedSeller;
  const pastSales = useQuery({
    queryKey: ['orders', 'SELLER', 'exists'],
    queryFn: () => call(api.GET('/api/v1/orders', { params: { query: { role: 'SELLER', size: 1 } } })),
    enabled: notVerified,
  });
  const showSales = !notVerified || (pastSales.data?.content?.length ?? 0) > 0;
  const tabs = showSales ? TABS : TABS.filter((tab) => tab.role === 'BUYER');
  const role: OrderRole = showSales && params.get('role') === 'SELLER' ? 'SELLER' : 'BUYER';
  const status = (params.get('status') || undefined) as OrderStatus | undefined;

  const update = (next: { role?: OrderRole; status?: string }) => {
    const merged = { role, status: status ?? '', ...next };
    const search: Record<string, string> = { role: merged.role };
    if (merged.status) search.status = merged.status;
    setParams(search, { replace: true });
  };

  const query = useInfiniteQuery({
    queryKey: ['orders', role, status ?? 'ALL'],
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/orders', { params: { query: { role, status, cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage, query.isFetchingNextPage, query.fetchNextPage);
  const orders = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <div className="mx-auto max-w-[880px]">
      <PageHeader title="주문 내역" description="경매에서 낙찰되면 주문이 자동으로 만들어져요." />

      {/* 예전 판매 주문 확인이 실패하면 판매 내역 탭이 숨겨진 채로 끝나지 않게 다시 확인할 수 있게 한다. */}
      {pastSales.isError && (
        <Notice tone="error" className="mb-4">
          예전 판매 주문을 확인하지 못해 판매 내역 탭을 숨겼어요. {errorMessage(pastSales.error)}{' '}
          <Button variant="outline" size="sm" className="ml-1" onClick={() => void pastSales.refetch()} loading={pastSales.isFetching}>
            다시 확인
          </Button>
        </Notice>
      )}
      <div className="mb-4 flex items-end justify-between gap-4">
        <div role="tablist" aria-label="주문 종류" className="flex gap-1 rounded-md bg-neutral-50 p-1">
          {tabs.map((tab) => (
            <button
              key={tab.role}
              type="button"
              role="tab"
              aria-selected={role === tab.role}
              onClick={() => update({ role: tab.role, status: '' })}
              className={cn(
                'h-9 rounded-sm px-4 text-body font-semibold transition-colors',
                role === tab.role ? 'bg-neutral-0 text-neutral-900 shadow-sm' : 'text-neutral-500 hover:text-neutral-900',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
        <div className="w-[200px]">
          <Select label="상태" hideLabel options={STATUS_OPTIONS} value={status ?? ''} onChange={(e) => update({ status: e.target.value })} />
        </div>
      </div>

      <Card>
        {query.isPending ? (
          <div className="flex flex-col gap-3 p-5">
            {Array.from({ length: 4 }, (_, i) => (
              <Skeleton key={i} className="h-20" />
            ))}
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : orders.length === 0 ? (
          <EmptyState
            title={role === 'BUYER' ? '구매한 주문이 없어요' : '판매한 주문이 없어요'}
            description={role === 'BUYER' ? '경매에서 낙찰받으면 여기에 표시돼요.' : '내 상품이 낙찰되면 여기에 표시돼요.'}
          />
        ) : (
          <ul className="divide-y divide-neutral-100">
            {orders.map((order) => (
              <li key={order.orderId}>
                <Link to={`/orders/${order.orderId}`} className="flex items-center gap-4 px-5 py-4 transition-colors hover:bg-neutral-25">
                  <ProductImage src={order.productImageUrl} alt={order.productName ?? '상품'} className="size-16" />
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <div className="flex items-center gap-2">
                      <OrderStatusBadge status={order.status} />
                      <span className="text-caption text-neutral-500">{order.createdAt ? formatDate(order.createdAt) : ''}</span>
                    </div>
                    <p className="truncate text-body-lg font-semibold">{order.productName ?? '-'}</p>
                  </div>
                  <div className="flex flex-col items-end gap-0.5">
                    <span className="text-caption text-neutral-500">낙찰가</span>
                    <span className="num text-body-lg font-bold">{formatPoint(order.finalPrice ?? 0)}</span>
                  </div>
                  <ChevronRight className="size-5 text-neutral-400" aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
        )}
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && (
          <div className="p-5">
            <Skeleton className="h-20" />
          </div>
        )}
      </Card>
    </div>
  );
}
