import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useMemo } from 'react';
import { api, call, type Schema } from '@/api';
import { ACTIVE_STREAM_KEY } from '@/components/broadcast/broadcast-context';
import { toLiveAuctionCreateRequest, type AuctionConditionValues } from '@/lib/auction';
import { liveStreamAuctionsKey, useLiveStreamAuctions } from '@/lib/use-live-stream-auctions';

type Auction = Schema<'AuctionResponse'>;

/** 진행 중인 경매의 현재가·입찰 수를 이 간격으로 다시 불러온다. (입찰 실시간 메시지가 아직 없음) */
const REFRESH_MS = 3000;
/** 스튜디오에서 고를 수 있는 내 상품 수 */
const PRODUCT_PAGE_SIZE = 50;
/** 내 시작 전·진행 중 경매 (GET /users/me/auctions). 미리 만든 경매를 방송에 올릴 때 쓴다. */
const MY_OPEN_AUCTIONS_KEY = ['auctions', 'mine', 'open'] as const;

/**
 * 판매자 스튜디오의 방송 중 경매: 내 판매 중 상품 + 이 방송에 올라간 경매(최신순).
 * - 새 경매: POST /lives/{id}/auctions (시작 즉시 진행 중)
 * - 미리 만든 경매: POST /lives/{id}/auctions/{auctionId} (방송에 올리기, 일정은 그대로)
 * - 취소: POST /auctions/{id}/cancel
 * - 영상 위에 띄우기·내리기: PUT·DELETE /live-streams/{id}/featured-auction (한 번에 하나)
 */
export function useLiveAuctions(liveStreamId: number, featuredAuctionId?: number) {
  const queryClient = useQueryClient();

  const products = useQuery({
    queryKey: ['products', 'mine', 'studio'],
    queryFn: () => call(api.GET('/api/v1/users/me/products', { params: { query: { page: 0, size: PRODUCT_PAGE_SIZE, sort: ['createdAt,desc'] } } })),
    select: (page) => (page?.content ?? []).filter((product) => product.status === 'ACTIVE'),
  });

  const liveQuery = useLiveStreamAuctions(liveStreamId, REFRESH_MS);
  const auctions = useMemo(() => liveQuery.data ?? [], [liveQuery.data]);
  const mine = useQuery({
    queryKey: MY_OPEN_AUCTIONS_KEY,
    queryFn: () => call(api.GET('/api/v1/users/me/auctions')),
  });

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: liveStreamAuctionsKey(liveStreamId) });
    void queryClient.invalidateQueries({ queryKey: MY_OPEN_AUCTIONS_KEY });
    void queryClient.invalidateQueries({ queryKey: ['auctions', 'list'] });
  }, [queryClient, liveStreamId]);

  const start = useMutation({
    mutationFn: ({ productId, values }: { productId: number; values: AuctionConditionValues }) =>
      call(api.POST('/api/v1/lives/{liveId}/auctions', { params: { path: { liveId: liveStreamId } }, body: toLiveAuctionCreateRequest(productId, values) })),
    onSuccess: refresh,
  });

  const show = useMutation({
    mutationFn: (auctionId: number) => call(api.POST('/api/v1/lives/{liveId}/auctions/{auctionId}', { params: { path: { liveId: liveStreamId, auctionId } } })),
    onSettled: refresh,
  });

  const cancel = useMutation({
    mutationFn: (auctionId: number) => call(api.POST('/api/v1/auctions/{auctionId}/cancel', { params: { path: { auctionId } } })),
    onSettled: (_data, _error, auctionId) => {
      void queryClient.invalidateQueries({ queryKey: ['auction', auctionId] });
      refresh();
    },
  });

  // 영상 위에 띄우기·내리기(null). 응답은 방송 정보라 스튜디오의 방송 정보를 바로 바꾼다. (시청 화면에는 방송 정보 변경 알림으로 반영)
  const feature = useMutation({
    mutationFn: (auctionId: number | null) =>
      auctionId === null
        ? call(api.DELETE('/api/v1/live-streams/{liveStreamId}/featured-auction', { params: { path: { liveStreamId } } }))
        : call(api.PUT('/api/v1/live-streams/{liveStreamId}/featured-auction', { params: { path: { liveStreamId } }, body: { auctionId } })),
    onSuccess: (saved) => {
      if (saved) queryClient.setQueryData(ACTIVE_STREAM_KEY, saved);
    },
    // 경매가 그새 끝났으면(LIVESTREAM_014) 목록을 새로 받아 버튼을 맞춘다.
    onError: refresh,
  });

  /** 상품의 시작 전·진행 중인 경매 (이 방송에 올라간 것을 먼저, 없으면 다른 곳에서 만든 내 경매) */
  const activeAuctionOf = useCallback(
    (productId?: number): Auction | undefined =>
      auctions.find((a) => a.productId === productId && (a.status === 'READY' || a.status === 'ONGOING')) ??
      (mine.data ?? []).find((a) => a.productId === productId),
    [auctions, mine.data],
  );

  return { liveStreamId, featuredAuctionId, products, auctions, loadingAuctions: liveQuery.isPending, start, show, cancel, feature, activeAuctionOf };
}

export type LiveAuctions = ReturnType<typeof useLiveAuctions>;
