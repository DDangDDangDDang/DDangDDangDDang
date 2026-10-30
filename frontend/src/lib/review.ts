import { useQuery, useQueryClient } from '@tanstack/react-query';
import { api, call, isErrorCode, type Schema } from '@/api';
import { formatNumber } from './format';
import { sellerProfileKey } from './use-seller';

/*
 * 판매자 리뷰 화면의 계산 로직과 캐시 키. (화면과 분리해 단위 테스트한다)
 */

type Trust = Schema<'Trust'>;
type Review = Schema<'ReviewResponse'>;

/** 리뷰 내용 최대 글자 수 (ReviewCreateRequest.content) */
export const REVIEW_CONTENT_MAX = 500;
/** 판매자 답글 최대 글자 수 (ReviewReplyRequest.content) */
export const REPLY_CONTENT_MAX = 300;

export const sellerReviewsKey = (sellerId: number) => ['seller-reviews', sellerId] as const;

/** 평균 별점을 "4.3"처럼 소수 첫째 자리까지. 리뷰가 없으면(null) undefined */
export function formatRating(average?: number | null): string | undefined {
  if (average === undefined || average === null) return undefined;
  return average.toFixed(1);
}

/** 구매자 만족도 칸: "★ 4.3 / 리뷰 3개". 받은 리뷰가 없으면 "아직 없음" */
export function satisfactionLabel(trust?: Trust): string {
  const rating = formatRating(trust?.averageRating);
  return rating === undefined ? '아직 없음' : `★ ${rating} / 리뷰 ${formatNumber(trust?.reviewCount ?? 0)}개`;
}

/** 리뷰 탭 맨 위 요약: "★ 4.3 · 리뷰 3개". 받은 리뷰가 없으면 undefined (빈 목록 안내만 보인다) */
export function reviewSummary(trust?: Trust): string | undefined {
  const rating = formatRating(trust?.averageRating);
  return rating === undefined ? undefined : `★ ${rating} · 리뷰 ${formatNumber(trust?.reviewCount ?? 0)}개`;
}

/** 로그인한 회원이 이 판매자 본인인지 (리뷰에 답글을 달 수 있는지) */
export function isOwnStore(sellerId: number, userId?: number): boolean {
  return userId !== undefined && userId === sellerId;
}

export const orderReviewKey = (orderId: number) => ['order-review', orderId] as const;

/** 주문에 내가 쓴 리뷰를 서버에서 불러온다. 아직 안 썼으면(REVIEW_001) 에러가 아니라 null. */
export async function fetchOrderReview(orderId: number): Promise<Review | null> {
  try {
    return (await call(api.GET('/api/v1/reviews/orders/{orderId}', { params: { path: { orderId } } }))) ?? null;
  } catch (error) {
    if (isErrorCode(error, 'REVIEW_001')) return null;
    throw error;
  }
}

/** 주문에 내가 쓴 리뷰. 쓰거나 고치거나 지운 뒤에는 화면에서 setQueryData로 바로 바꾼다. */
export function useOrderReview(orderId: number) {
  return useQuery<Review | null>({
    queryKey: orderReviewKey(orderId),
    queryFn: () => fetchOrderReview(orderId),
  });
}

/** 리뷰를 쓰거나 고치거나 지운 뒤: 판매자 리뷰 목록과 구매자 만족도(프로필)를 다시 불러온다. */
export function useRefreshSellerReviews() {
  const queryClient = useQueryClient();
  return (sellerId?: number) => {
    if (sellerId === undefined) return;
    void queryClient.invalidateQueries({ queryKey: sellerReviewsKey(sellerId) });
    void queryClient.invalidateQueries({ queryKey: sellerProfileKey(sellerId) });
  };
}
