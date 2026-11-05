import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, CardBody, ConfirmDialog, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { formatDateTime, formatPoint } from '@/lib/format';
import { COURIER_LABEL, ORDER_ISSUE_TYPE_LABEL, ORDER_REVIEW_REASON_LABEL } from '@/lib/labels';
import { requireOrderId } from '@/lib/order-view';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Order = Schema<'OrderResponse'>;
type Action = 'confirm' | 'refund';

const PAGE_SIZE = 20;
const queryKey = ['admin', 'order-reviews'] as const;

/**
 * 주문 확인 (관리자): 구매자가 문제를 신고했거나 송장을 확인하지 못해 자동 구매 확정이 멈춘 주문.
 * 배송 조회·메신저로 확인한 뒤 판매자 정산 또는 구매자 전액 환불로 마무리한다. 먼저 들어온 순으로 보여 준다.
 */
export function OrderReviewsAdminPage() {
  const session = useSession();
  if (session?.user.role !== 'ADMIN') {
    return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="자동 구매 확정이 멈춘 주문은 관리자가 확인해요." />;
  }
  return (
    <div className="mx-auto max-w-[960px]">
      <PageHeader
        title="주문 확인"
        description="구매자가 문제를 신고했거나 송장으로 배송 완료를 확인하지 못해 자동 구매 확정이 멈춘 주문이에요. 확인한 뒤 정산하거나 환불해 주세요."
      />
      <ReviewList />
    </div>
  );
}

function ReviewList() {
  const query = useInfiniteQuery({
    queryKey,
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/admin/orders/reviews', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage, query.isFetchingNextPage, query.fetchNextPage);
  const orders = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-3">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-40" />
        ))}
      </div>
    );
  }
  if (query.isError) {
    return (
      <Card>
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      </Card>
    );
  }
  if (orders.length === 0) {
    return (
      <Card>
        <EmptyState title="확인할 주문이 없어요" description="구매자가 문제를 신고하거나 송장 확인이 안 된 주문이 여기에 나와요." />
      </Card>
    );
  }
  return (
    <div className="flex flex-col gap-3">
      {orders.map((order) => (
        <ReviewCard key={order.orderId} order={order} />
      ))}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && <Skeleton className="h-40" />}
    </div>
  );
}

function ReviewCard({ order }: { order: Order }) {
  const queryClient = useQueryClient();
  const [action, setAction] = useState<Action>();
  const mutation = useMutation({
    mutationFn: (next: Action) => {
      const params = { params: { path: { orderId: requireOrderId(order) } } };
      return next === 'confirm'
        ? call(api.POST('/api/v1/admin/orders/{orderId}/review/confirm', params))
        : call(api.POST('/api/v1/admin/orders/{orderId}/review/refund', params));
    },
    onSuccess: () => {
      setAction(undefined);
      void queryClient.invalidateQueries({ queryKey });
    },
  });

  const items: [string, string][] = [
    ['주문번호', order.orderNo ?? '-'],
    ['낙찰가', formatPoint(order.finalPrice ?? 0)],
    ['판매자 정산 금액', formatPoint(order.settlementAmount ?? 0)],
    ['구매자 · 판매자', `회원 ${order.buyerId ?? '-'} · 회원 ${order.sellerId ?? '-'}`],
    ['송장', order.courier ? `${COURIER_LABEL[order.courier]} ${order.trackingNo ?? ''}` : '-'],
    ['발송', order.shippedAt ? formatDateTime(order.shippedAt) : '-'],
    ['확인 요청', order.reviewRequestedAt ? formatDateTime(order.reviewRequestedAt) : '-'],
  ];

  return (
    <Card>
      <CardBody className="flex flex-col gap-3 pt-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex min-w-0 flex-col gap-1">
            <div className="flex items-center gap-2">
              {order.reviewReason && <Badge variant="warning">{ORDER_REVIEW_REASON_LABEL[order.reviewReason]}</Badge>}
              {order.issueType && <span className="text-label font-semibold">{ORDER_ISSUE_TYPE_LABEL[order.issueType]}</span>}
            </div>
            {/* 주문 상세(/orders/:id)는 구매자·판매자만 볼 수 있어 링크를 걸지 않는다. 확인에 필요한 정보는 이 카드에 다 있다. */}
            <h2 className="truncate text-title-md font-bold">{order.productName ?? '-'}</h2>
          </div>
          <div className="flex shrink-0 gap-2">
            <Button variant="outline" onClick={() => setAction('refund')}>
              구매자 환불
            </Button>
            <Button onClick={() => setAction('confirm')}>판매자 정산</Button>
          </div>
        </div>
        {order.issueDetail && <p className="whitespace-pre-wrap rounded-md bg-neutral-50 px-3 py-2 text-body">{order.issueDetail}</p>}
        <dl className="grid grid-cols-[120px_1fr] gap-y-1 text-label">
          {items.map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-neutral-500">{label}</dt>
              <dd className="num">{value}</dd>
            </div>
          ))}
        </dl>
        {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      </CardBody>
      <ConfirmDialog
        open={action !== undefined}
        title={action === 'refund' ? '구매자에게 전액 환불할까요?' : '판매자에게 정산할까요?'}
        description={
          action === 'refund'
            ? `주문을 취소하고 ${formatPoint(order.finalPrice ?? 0)}를 구매자에게 돌려줘요. 되돌릴 수 없어요.`
            : `구매 확정하고 판매 수수료를 뺀 ${formatPoint(order.settlementAmount ?? 0)}를 판매자에게 지급해요. 되돌릴 수 없어요.`
        }
        confirmLabel={action === 'refund' ? '환불' : '정산'}
        loading={mutation.isPending}
        onConfirm={() => action && mutation.mutate(action)}
        onCancel={() => setAction(undefined)}
      />
    </Card>
  );
}
