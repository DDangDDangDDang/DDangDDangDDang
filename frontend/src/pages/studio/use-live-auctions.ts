import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useState } from 'react';
import { api, call, type Schema } from '@/api';
import { toLiveAuctionCreateRequest, type AuctionConditionValues } from '@/lib/auction';

type Auction = Schema<'AuctionResponse'>;

/** 진행 중인 경매의 현재가·입찰 수를 이 간격으로 다시 불러온다. (경매 실시간 메시지가 아직 없음) */
const REFRESH_MS = 3000;
/** 스튜디오에서 고를 수 있는 내 상품 수 */
const PRODUCT_PAGE_SIZE = 50;

function storageKey(liveStreamId: number) {
  return `studio-auctions-${liveStreamId}`;
}

/** 이 방송에서 시작한 경매 ID. 방송별 경매 목록 API가 없어서 새로고침해도 남도록 이 창(sessionStorage)에 적어 둔다. */
function readIds(liveStreamId: number): number[] {
  try {
    const parsed: unknown = JSON.parse(sessionStorage.getItem(storageKey(liveStreamId)) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

function writeIds(liveStreamId: number, ids: number[]) {
  try {
    sessionStorage.setItem(storageKey(liveStreamId), JSON.stringify(ids));
  } catch {
    // 저장하지 못해도 화면에는 남아 있다.
  }
}

/**
 * 판매자 스튜디오의 방송 중 경매: 내 판매 중 상품 + 이 방송에서 시작한 경매(최신순).
 * 경매 시작은 POST /lives/{id}/auctions(시작 즉시 진행 중), 취소는 POST /auctions/{id}/cancel.
 */
export function useLiveAuctions(liveStreamId: number) {
  const queryClient = useQueryClient();
  const [auctionIds, setAuctionIds] = useState<number[]>(() => readIds(liveStreamId));
  useEffect(() => writeIds(liveStreamId, auctionIds), [liveStreamId, auctionIds]);

  const products = useQuery({
    queryKey: ['products', 'mine', 'studio'],
    queryFn: () => call(api.GET('/api/v1/users/me/products', { params: { query: { page: 0, size: PRODUCT_PAGE_SIZE, sort: ['createdAt,desc'] } } })),
    select: (page) => (page?.content ?? []).filter((product) => product.status === 'ACTIVE'),
  });

  const auctionQueries = useQueries({
    queries: auctionIds.map((auctionId) => ({
      queryKey: ['auction', auctionId],
      queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } })),
      refetchInterval: (q: { state: { data?: Auction } }) => (q.state.data?.status === 'ONGOING' ? REFRESH_MS : false),
    })),
  });
  const auctions = auctionQueries.map((q) => q.data).filter((a): a is Auction => a !== undefined);

  const start = useMutation({
    mutationFn: ({ productId, values }: { productId: number; values: AuctionConditionValues }) =>
      call(api.POST('/api/v1/lives/{liveId}/auctions', { params: { path: { liveId: liveStreamId } }, body: toLiveAuctionCreateRequest(productId, values) })),
    onSuccess: (auction) => {
      if (!auction?.id) return;
      const auctionId = auction.id;
      queryClient.setQueryData(['auction', auctionId], auction);
      setAuctionIds((ids) => [auctionId, ...ids.filter((id) => id !== auctionId)]);
      void queryClient.invalidateQueries({ queryKey: ['auctions', 'list'] });
    },
  });

  const cancel = useMutation({
    mutationFn: (auctionId: number) => call(api.POST('/api/v1/auctions/{auctionId}/cancel', { params: { path: { auctionId } } })),
    onSettled: (_data, _error, auctionId) => void queryClient.invalidateQueries({ queryKey: ['auction', auctionId] }),
  });

  /** 상품에 시작 전·진행 중인 경매가 있는지 (이 방송에서 시작한 경매 기준) */
  const activeAuctionOf = useCallback(
    (productId?: number) => auctions.find((a) => a.productId === productId && (a.status === 'READY' || a.status === 'ONGOING')),
    [auctions],
  );

  return { products, auctions, loadingAuctions: auctionQueries.some((q) => q.isPending), start, cancel, activeAuctionOf };
}

export type LiveAuctions = ReturnType<typeof useLiveAuctions>;
