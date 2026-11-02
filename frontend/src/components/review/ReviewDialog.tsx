import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, isErrorCode, type Schema } from '@/api';
import { Button, Dialog, Notice, Textarea } from '@/components/ui';
import { formatNumber } from '@/lib/format';
import { REVIEW_CONTENT_MAX } from '@/lib/review';
import { RatingInput } from './Stars';

type Review = Schema<'ReviewResponse'>;

type ReviewDialogProps = {
  open: boolean;
  orderId: number;
  /** 고칠 리뷰. 없으면 새로 쓴다. */
  review?: Review;
  /** 창 제목 (구매 확정 직후에는 "거래는 어떠셨나요?") */
  title?: string;
  /** 닫기 버튼 문구. 구매 확정 직후에는 "건너뛰기" */
  closeLabel?: string;
  onClose: () => void;
  onSaved: (review: Review) => void;
  /** 이미 리뷰를 쓴 주문이었을 때 (REVIEW_004) */
  onAlreadyReviewed?: () => void;
};

/** 리뷰 쓰기·고치기 창. 별점은 꼭 고르고, 내용은 비워도 된다. */
export function ReviewDialog(props: ReviewDialogProps) {
  const { open, review, title, onClose } = props;
  return (
    <Dialog open={open} title={title ?? (review ? '리뷰 수정' : '리뷰 쓰기')} description="별점은 꼭 골라 주세요. 내용은 쓰지 않아도 돼요." onClose={onClose} className="w-[520px]">
      {/* 열 때마다 입력을 처음 값으로 다시 채운다. */}
      <ReviewForm {...props} />
    </Dialog>
  );
}

function ReviewForm({ orderId, review, closeLabel = '취소', onClose, onSaved, onAlreadyReviewed }: ReviewDialogProps) {
  const [rating, setRating] = useState(review?.rating ?? 0);
  const [content, setContent] = useState(review?.content ?? '');
  const [ratingError, setRatingError] = useState<string>();

  const mutation = useMutation({
    mutationFn: () => {
      const body = { rating, content: content.trim() || undefined };
      return review?.reviewId !== undefined
        ? call(api.PATCH('/api/v1/reviews/{reviewId}', { params: { path: { reviewId: review.reviewId } }, body }))
        : call(api.POST('/api/v1/reviews', { body: { orderId, ...body } }));
    },
    onSuccess: (saved) => {
      if (saved) onSaved(saved);
    },
    onError: (error) => {
      if (isErrorCode(error, 'REVIEW_004')) onAlreadyReviewed?.();
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (rating < 1) {
      setRatingError('별점을 골라 주세요.');
      return;
    }
    mutation.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <RatingInput
        label="별점"
        value={rating}
        onChange={(value) => {
          setRating(value);
          setRatingError(undefined);
        }}
        error={ratingError}
      />
      <Textarea
        label="내용 (선택)"
        placeholder="상품 상태, 발송 속도, 판매자 응대는 어땠나요?"
        value={content}
        maxLength={REVIEW_CONTENT_MAX}
        onChange={(e) => setContent(e.target.value)}
        hint={
          <span className="num">
            {formatNumber(content.length)}/{formatNumber(REVIEW_CONTENT_MAX)}
          </span>
        }
      />
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
          {closeLabel}
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          {review ? '수정하기' : '리뷰 남기기'}
        </Button>
      </div>
    </form>
  );
}
