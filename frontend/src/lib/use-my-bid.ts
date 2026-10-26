import { useQuery } from '@tanstack/react-query';
import { api, call, useSession } from '@/api';
import { auctionKey } from './use-auction-realtime';

/**
 * 이 경매에서 내 입찰 상태 (최고 입찰 중 / 밀림 / 낙찰 / 낙찰 실패). 로그인했을 때만 불러온다.
 * 쿼리 키가 경매 키(['auction', id]) 아래라, 실시간 입찰 메시지로 경매를 다시 불러올 때 함께 다시 불러온다.
 */
export function useMyBid(auctionId: number, enabled = true) {
  const session = useSession();
  return useQuery({
    queryKey: [...auctionKey(auctionId), 'my-bid'],
    queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}/bids/me', { params: { path: { auctionId } } })),
    enabled: enabled && session !== null,
  });
}
