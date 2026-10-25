import { useQuery } from '@tanstack/react-query';
import { api, call } from '@/api';

/** 방송에 올라간 경매 목록의 쿼리 키. 경매 변경 알림(/topic/live-streams/{id}/auctions)이 오면 useLiveRealtime이 다시 불러온다. */
/** 시청 화면에서 진행 중인 경매의 현재가·입찰 수를 이 간격으로 다시 불러온다. (경매 카드와 영상 위 경매가 같이 씀) */
export const WATCH_AUCTION_REFRESH_MS = 5000;

export const liveStreamAuctionsKey = (liveStreamId: number) => ['live-stream-auctions', liveStreamId] as const;

/**
 * 방송에 올라간 경매(GET /lives/{id}/auctions, 최신순, 끝난 경매 포함). 시청 화면과 스튜디오가 같이 쓴다.
 * 진행 중인 경매가 있으면 현재가·입찰 수를 refreshMs 간격으로 다시 불러온다. (입찰 실시간 메시지가 아직 없음)
 */
export function useLiveStreamAuctions(liveStreamId: number, refreshMs: number) {
  return useQuery({
    queryKey: liveStreamAuctionsKey(liveStreamId),
    queryFn: () => call(api.GET('/api/v1/lives/{liveId}/auctions', { params: { path: { liveId: liveStreamId } } })),
    refetchInterval: (q) => ((q.state.data ?? []).some((a) => a.status === 'ONGOING' || a.status === 'READY') ? refreshMs : false),
  });
}
