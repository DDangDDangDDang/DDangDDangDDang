import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { connectRealtime, REALTIME, useSession, type AuctionRealtimeMessage, type Schema } from '@/api';

type Auction = Schema<'AuctionResponse'>;

/** 경매 한 건 쿼리 키 (경매 상세·입찰 창이 같은 캐시를 쓴다) */
export const auctionKey = (auctionId: number) => ['auction', auctionId] as const;

/**
 * 경매 한 건을 실시간으로 받는다. (`/topic/auctions/{id}`, 손님도)
 * - 입찰이 반영되면 현재가·입찰 수·마감 시각을 바로 바꾸고, 내 사용 가능 포인트도 다시 불러온다. (입찰·밀려남으로 묶인 포인트가 바뀜)
 * - 경매가 끝나면 경매를 다시 불러와 낙찰·유찰 상태를 보여 준다.
 * 화면을 떠나면 연결을 끊는다. 실시간이 끊겨도 각 화면의 주기적 새로 불러오기가 남아 있다.
 */
export function useAuctionRealtime(auctionId: number | undefined, enabled = true) {
  const queryClient = useQueryClient();
  const loggedIn = useSession() !== null;

  useEffect(() => {
    if (auctionId === undefined || !enabled) return;
    const connection = connectRealtime(() => undefined, { guest: !loggedIn });
    connection.subscribe<AuctionRealtimeMessage>(REALTIME.auction(auctionId), (message) => {
      if ('amount' in message) {
        queryClient.setQueryData<Auction>(auctionKey(auctionId), (prev) =>
          prev ? { ...prev, currentPrice: message.amount, bidCount: message.bidCount, endAt: message.endAt } : prev,
        );
        if (loggedIn) void queryClient.invalidateQueries({ queryKey: ['points', 'wallet'] });
      }
      void queryClient.invalidateQueries({ queryKey: auctionKey(auctionId) });
    });
    return () => connection.close();
  }, [auctionId, enabled, loggedIn, queryClient]);
}
