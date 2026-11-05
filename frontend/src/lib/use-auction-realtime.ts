import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { connectRealtime, REALTIME, useSession, type AuctionRealtimeMessage, type Schema } from '@/api';

type Auction = Schema<'AuctionResponse'>;

/** 경매 한 건 쿼리 키 (경매 상세·입찰 창이 같은 캐시를 쓴다) */
export const auctionKey = (auctionId: number) => ['auction', auctionId] as const;

/** 입찰이 몰릴 때 입찰 기록·내 입찰 상태를 다시 부르기 전에 모으는 시간. 이 시간 안의 입찰은 한 번에 반영한다 */
export const BID_REFRESH_DELAY_MS = 1_000;

/**
 * 경매 한 건을 실시간으로 받는다. (`/topic/auctions/{id}`, 손님도)
 * - 입찰이 반영되면 메시지에 든 현재가·입찰 수·마감 시각을 캐시에 바로 넣는다. 경매 자체는 다시 부르지 않는다.
 * - 입찰 기록·내 입찰 상태는 1초 동안 모았다가 한 번만 다시 부른다.
 *   (보는 사람 모두가 입찰마다 요청을 보내면, 입찰이 몰릴 때 요청이 보는 사람 수 × 입찰 수만큼 쏟아진다)
 * - 내 포인트는 내가 최고 입찰자였을 때만(밀려나 묶인 포인트가 풀림) 다시 부른다.
 * - 경매가 끝나면 경매를 다시 불러와 낙찰·유찰 상태를 보여 준다.
 * 화면을 떠나면 연결을 끊는다. 실시간이 끊겨도 각 화면의 주기적 새로 불러오기가 남아 있다.
 */
export function useAuctionRealtime(auctionId: number | undefined, enabled = true) {
  const queryClient = useQueryClient();
  const loggedIn = useSession() !== null;

  useEffect(() => {
    if (auctionId === undefined || !enabled) return;
    let refreshTimer: ReturnType<typeof setTimeout> | undefined;
    const refreshBidViews = () => {
      if (refreshTimer !== undefined) return; // 이미 다시 부르기로 했으면 그 한 번에 맡긴다
      refreshTimer = setTimeout(() => {
        refreshTimer = undefined;
        void queryClient.invalidateQueries({ queryKey: [...auctionKey(auctionId), 'bids'] });
        void queryClient.invalidateQueries({ queryKey: [...auctionKey(auctionId), 'my-bid'] });
      }, BID_REFRESH_DELAY_MS);
    };

    const connection = connectRealtime(() => undefined, { guest: !loggedIn });
    connection.subscribe<AuctionRealtimeMessage>(REALTIME.auction(auctionId), (message) => {
      if ('amount' in message) {
        queryClient.setQueryData<Auction>(auctionKey(auctionId), (prev) =>
          prev ? { ...prev, currentPrice: message.amount, bidCount: message.bidCount, endAt: message.endAt } : prev,
        );
        // 내가 최고 입찰자였다면 이 입찰로 밀려나 묶인 포인트가 풀렸다
        const myBid = queryClient.getQueryData<{ state?: string }>([...auctionKey(auctionId), 'my-bid']);
        if (loggedIn && myBid?.state === 'LEADING') void queryClient.invalidateQueries({ queryKey: ['points', 'wallet'] });
        refreshBidViews();
        return;
      }
      // 경매 종료: 결과(낙찰·유찰)를 보여 주려 경매와 입찰 기록·내 입찰 상태를 모두 다시 부른다
      void queryClient.invalidateQueries({ queryKey: auctionKey(auctionId) });
      if (loggedIn) void queryClient.invalidateQueries({ queryKey: ['points', 'wallet'] });
    });
    return () => {
      if (refreshTimer !== undefined) clearTimeout(refreshTimer);
      connection.close();
    };
  }, [auctionId, enabled, loggedIn, queryClient]);
}
