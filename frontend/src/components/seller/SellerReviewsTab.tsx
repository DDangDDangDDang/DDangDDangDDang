import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { CornerDownRight } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { StarRating } from '@/components/review/Stars';
import { Button, ConfirmDialog, EmptyState, ErrorState, Notice, Skeleton, Textarea } from '@/components/ui';
import { formatDotDate, formatNumber } from '@/lib/format';
import { isOwnStore, REPLY_CONTENT_MAX, reviewSummary, sellerReviewsKey } from '@/lib/review';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Review = Schema<'ReviewResponse'>;
type Trust = Schema<'Trust'>;

/** 리뷰를 한 번에 불러오는 수 */
const REVIEW_PAGE_SIZE = 10;

/**
 * 판매자가 받은 리뷰: 맨 위에 평균 별점·개수, 아래에 최신 리뷰부터 10개씩. 끝까지 내리면 다음 10개를 불러온다.
 * 판매자 본인이 보면 리뷰마다 답글을 달거나 고치고 지울 수 있다.
 */
export function SellerReviewsTab({ sellerId, trust }: { sellerId: number; trust?: Trust }) {
  const session = useSession();
  const own = isOwnStore(sellerId, session?.user.userId);
  const summary = reviewSummary(trust);
  const query = useInfiniteQuery({
    queryKey: sellerReviewsKey(sellerId),
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/reviews', { params: { query: { sellerId, cursor: pageParam, size: REVIEW_PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  // 다음 페이지를 못 불러오면 자동으로 다시 요청하지 않는다. ([다시 시도]로)
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const reviews = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  let body;
  if (query.isPending) {
    body = (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-16" />
        ))}
      </div>
    );
  } else if (query.isError && reviews.length === 0) {
    body = <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  } else if (reviews.length === 0) {
    body = <EmptyState title="아직 리뷰가 없어요" description="이 판매자와 거래하고 구매 확정한 구매자의 리뷰가 여기에 나와요." />;
  } else {
    body = (
      <div className="flex max-h-[420px] flex-col overflow-y-auto">
        <ul aria-label="리뷰" className="divide-y divide-neutral-100">
          {reviews.map((review) => (
            <ReviewItem key={review.reviewId} review={review} sellerId={sellerId} own={own} />
          ))}
        </ul>
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && <Skeleton className="my-2 h-16" />}
        {query.isError && <ErrorState message={errorMessage(query.error)} onRetry={() => void query.fetchNextPage()} />}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {summary && <p className="num text-body-lg font-bold">{summary}</p>}
      {body}
    </div>
  );
}

function ReviewItem({ review, sellerId, own }: { review: Review; sellerId: number; own: boolean }) {
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const queryClient = useQueryClient();
  const reviewId = review.reviewId ?? 0;
  const refresh = () => queryClient.invalidateQueries({ queryKey: sellerReviewsKey(sellerId) });

  const remove = useMutation({
    mutationFn: () => call(api.DELETE('/api/v1/reviews/{reviewId}/reply', { params: { path: { reviewId } } })),
    onSuccess: async () => {
      await refresh();
      setDeleting(false);
    },
  });

  return (
    <li className="flex flex-col gap-2 px-1 py-3">
      <div className="flex items-center gap-2">
        <StarRating rating={review.rating ?? 0} />
        <strong className="min-w-0 flex-1 truncate text-body font-semibold">{review.productName || '이름 없는 상품'}</strong>
        <span className="num shrink-0 text-caption text-neutral-500">{review.createdAt ? formatDotDate(review.createdAt) : ''}</span>
      </div>
      {review.content && <p className="line-clamp-2 text-body whitespace-pre-line text-neutral-700">{review.content}</p>}

      {review.reply && !editing && (
        <div className="flex gap-2 rounded-md bg-neutral-50 px-3 py-2.5">
          <CornerDownRight className="mt-0.5 size-4 shrink-0 text-neutral-400" aria-hidden="true" />
          <div className="flex min-w-0 flex-col gap-1">
            <span className="text-caption font-semibold text-neutral-500">
              판매자 답글
              {review.reply.repliedAt && <span className="num font-normal"> · {formatDotDate(review.reply.repliedAt)}</span>}
            </span>
            <p className="text-body whitespace-pre-line text-neutral-700">{review.reply.content}</p>
          </div>
        </div>
      )}

      {own &&
        (editing ? (
          <ReplyForm review={review} onDone={() => setEditing(false)} onSaved={refresh} />
        ) : (
          <div className="flex justify-end gap-1.5">
            {review.reply ? (
              <>
                <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                  답글 수정
                </Button>
                <Button variant="ghost" size="sm" onClick={() => setDeleting(true)}>
                  답글 삭제
                </Button>
              </>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                답글 달기
              </Button>
            )}
          </div>
        ))}
      {remove.isError && <Notice tone="error">{errorMessage(remove.error)}</Notice>}

      <ConfirmDialog
        open={deleting}
        title="답글을 삭제할까요?"
        description="삭제한 답글은 되돌릴 수 없어요. 리뷰는 그대로 남아요."
        confirmLabel="삭제"
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
        onCancel={() => setDeleting(false)}
      />
    </li>
  );
}

/** 답글 달기·고치기 (1~300자). 저장하면 목록을 다시 불러온다. */
function ReplyForm({ review, onDone, onSaved }: { review: Review; onDone: () => void; onSaved: () => Promise<unknown> }) {
  const [content, setContent] = useState(review.reply?.content ?? '');
  const [inputError, setInputError] = useState<string>();
  const mutation = useMutation({
    mutationFn: () =>
      call(api.PUT('/api/v1/reviews/{reviewId}/reply', { params: { path: { reviewId: review.reviewId ?? 0 } }, body: { content: content.trim() } })),
    onSuccess: async () => {
      await onSaved();
      onDone();
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!content.trim()) {
      setInputError('답글 내용을 입력해 주세요.');
      return;
    }
    mutation.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <Textarea
        label={review.reply ? '답글 수정' : '답글'}
        hideLabel
        placeholder="구매자에게 감사 인사나 안내를 남겨 보세요."
        value={content}
        maxLength={REPLY_CONTENT_MAX}
        onChange={(e) => {
          setContent(e.target.value);
          setInputError(undefined);
        }}
        error={inputError}
        hint={
          <span className="num">
            {formatNumber(content.length)}/{formatNumber(REPLY_CONTENT_MAX)}
          </span>
        }
        className="min-h-20"
      />
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end gap-1.5">
        <Button variant="ghost" size="sm" onClick={onDone} disabled={mutation.isPending}>
          취소
        </Button>
        <Button type="submit" size="sm" loading={mutation.isPending}>
          {review.reply ? '답글 저장' : '답글 등록'}
        </Button>
      </div>
    </form>
  );
}
