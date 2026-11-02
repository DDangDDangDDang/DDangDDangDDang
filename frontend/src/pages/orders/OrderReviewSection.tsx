import { useMutation, useQueryClient } from '@tanstack/react-query';
import { PenLine } from 'lucide-react';
import { useState } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { ReviewDialog } from '@/components/review/ReviewDialog';
import { StarRating } from '@/components/review/Stars';
import { Button, ConfirmDialog, Notice, Skeleton } from '@/components/ui';
import { orderReviewKey, useOrderReview, useRefreshSellerReviews } from '@/lib/review';

type Order = Schema<'OrderResponse'>;
type Review = Schema<'ReviewResponse'>;

/**
 * 구매 확정한 주문의 리뷰. 서버에서 내 리뷰를 불러와, 없으면 [리뷰 쓰기], 있으면 고치거나 지울 수 있다.
 * 구매 확정 직후(prompted)에는 "거래는 어떠셨나요?" 창을 바로 띄운다. (건너뛰어도 나중에 쓸 수 있음)
 */
export function OrderReviewSection({ order, prompted = false }: { order: Order; prompted?: boolean }) {
  const orderId = order.orderId ?? 0;
  const queryClient = useQueryClient();
  const refreshSeller = useRefreshSellerReviews();
  const query = useOrderReview(orderId);
  const [dialog, setDialog] = useState<'prompt' | 'write' | null>(prompted ? 'prompt' : null);
  const [deleting, setDeleting] = useState(false);
  const [alreadyReviewed, setAlreadyReviewed] = useState(false);
  const written = query.data ?? undefined;
  const empty = query.isSuccess && query.data === null;

  // 먼저 시작한 조회가 늦게 끝나 방금 바꾼 값을 덮지 않도록, 진행 중인 조회를 취소한 뒤 바꾼다.
  const setReview = async (next: Review | null) => {
    await queryClient.cancelQueries({ queryKey: orderReviewKey(orderId) });
    queryClient.setQueryData(orderReviewKey(orderId), next);
  };

  const remove = useMutation({
    mutationFn: () => call(api.DELETE('/api/v1/reviews/{reviewId}', { params: { path: { reviewId: written?.reviewId ?? 0 } } })),
    onSuccess: async () => {
      await setReview(null);
      setAlreadyReviewed(false);
      setDeleting(false);
      refreshSeller(order.sellerId);
    },
  });

  return (
    <section aria-labelledby="order-review-title" className="flex flex-col gap-3 border-t border-neutral-100 pt-4">
      <div className="flex items-center justify-between gap-3">
        <h3 id="order-review-title" className="text-body font-bold">
          내 리뷰
        </h3>
        {empty && (
          <Button size="sm" onClick={() => setDialog('write')}>
            <PenLine className="size-4" aria-hidden="true" />
            리뷰 쓰기
          </Button>
        )}
        {written && (
          <div className="flex gap-1.5">
            <Button variant="outline" size="sm" onClick={() => setDialog('write')}>
              수정
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setDeleting(true)}>
              삭제
            </Button>
          </div>
        )}
      </div>

      {query.isPending && <Skeleton className="h-16" />}
      {query.isError && (
        <div className="flex items-center justify-between gap-3">
          <Notice tone="error">{errorMessage(query.error)}</Notice>
          <Button variant="outline" size="sm" onClick={() => void query.refetch()}>
            다시 시도
          </Button>
        </div>
      )}
      {empty && <p className="text-body text-neutral-500">판매자와의 거래는 어떠셨나요? 별점만 남겨도 다른 구매자에게 도움이 돼요.</p>}
      {alreadyReviewed && written && <Notice tone="info">이미 작성한 리뷰가 있어요. 아래 리뷰를 고치거나 지울 수 있어요.</Notice>}
      {written && (
        <div className="flex flex-col gap-1.5 rounded-md bg-neutral-25 px-4 py-3">
          <StarRating rating={written.rating ?? 0} />
          {written.content && <p className="text-body whitespace-pre-line text-neutral-700">{written.content}</p>}
        </div>
      )}
      {remove.isError && <Notice tone="error">{errorMessage(remove.error)}</Notice>}

      <ReviewDialog
        open={dialog !== null}
        orderId={orderId}
        review={written}
        title={dialog === 'prompt' ? '거래는 어떠셨나요?' : undefined}
        closeLabel={dialog === 'prompt' ? '건너뛰기' : '취소'}
        onClose={() => setDialog(null)}
        onSaved={(review) => {
          void setReview(review);
          setDialog(null);
          refreshSeller(review.sellerId ?? order.sellerId);
        }}
        // 다른 탭 등에서 이미 쓴 리뷰: 쓰던 초안으로 그 리뷰를 덮지 않도록 창을 닫고, 서버 리뷰를 불러와 보여준다.
        onAlreadyReviewed={() => {
          setDialog(null);
          setAlreadyReviewed(true);
          void queryClient.invalidateQueries({ queryKey: orderReviewKey(orderId) });
        }}
      />
      <ConfirmDialog
        open={deleting}
        title="리뷰를 삭제할까요?"
        description="삭제한 리뷰는 되돌릴 수 없어요."
        confirmLabel="삭제"
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
        onCancel={() => setDeleting(false)}
      />
    </section>
  );
}
