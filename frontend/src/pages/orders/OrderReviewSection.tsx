import { useMutation, useQueryClient } from '@tanstack/react-query';
import { PenLine } from 'lucide-react';
import { useState } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { ReviewDialog } from '@/components/review/ReviewDialog';
import { StarRating } from '@/components/review/Stars';
import { Button, ConfirmDialog, Notice } from '@/components/ui';
import { orderReviewKey, useOrderReview, useRefreshSellerReviews, type OrderReviewState } from '@/lib/review';

type Order = Schema<'OrderResponse'>;

/**
 * 구매 확정한 주문의 리뷰. [리뷰 쓰기]로 쓰고, 이 화면에서 쓴 리뷰는 고치거나 지울 수 있다.
 * 구매 확정 직후(prompted)에는 "거래는 어떠셨나요?" 창을 바로 띄운다. (건너뛰어도 나중에 쓸 수 있음)
 */
export function OrderReviewSection({ order, prompted = false }: { order: Order; prompted?: boolean }) {
  const orderId = order.orderId ?? 0;
  const queryClient = useQueryClient();
  const refreshSeller = useRefreshSellerReviews();
  const state = useOrderReview(orderId);
  const [dialog, setDialog] = useState<'prompt' | 'write' | null>(prompted ? 'prompt' : null);
  const [deleting, setDeleting] = useState(false);
  const written = state?.status === 'written' ? state.review : undefined;

  const setState = (next: OrderReviewState | null) => queryClient.setQueryData(orderReviewKey(orderId), next);

  const remove = useMutation({
    mutationFn: () => call(api.DELETE('/api/v1/reviews/{reviewId}', { params: { path: { reviewId: written?.reviewId ?? 0 } } })),
    onSuccess: () => {
      setState(null);
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
        {!state && (
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

      {!state && <p className="text-body text-neutral-500">판매자와의 거래는 어떠셨나요? 별점만 남겨도 다른 구매자에게 도움이 돼요.</p>}
      {state?.status === 'already' && <Notice tone="info">이미 리뷰를 작성했어요. 판매자 프로필의 리뷰 탭에서 볼 수 있어요.</Notice>}
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
          setState({ status: 'written', review });
          setDialog(null);
          refreshSeller(review.sellerId ?? order.sellerId);
        }}
        onAlreadyReviewed={() => setState({ status: 'already' })}
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
