/**
 * 방송 중인 판매자가 시청자 경로(/lives/:id)로 자기 방송에 들어오면 스튜디오로 보낸다.
 * 스튜디오의 [시청 화면 열기]로 연 탭만 시청자 화면을 보여 준다. (시청자에게 어떻게 보이는지 확인용)
 */
const VIEWER_PREVIEW_PARAM = 'viewer';

/** 스튜디오에서 내 방송을 시청자 화면으로 여는 주소 */
export function sellerViewerPath(liveStreamId: number): string {
  return `/lives/${liveStreamId}?${VIEWER_PREVIEW_PARAM}=1`;
}

/** 이 시청 화면을 스튜디오로 보낼지: 로그인한 사용자의 방송 중인 방송이고, 스튜디오에서 연 시청자 화면이 아닐 때 */
export function shouldOpenStudio(
  liveStream: { status?: string; sellerId?: number } | undefined,
  userId: number | undefined,
  search: URLSearchParams,
): boolean {
  return (
    liveStream?.status === 'LIVE' &&
    userId !== undefined &&
    liveStream.sellerId === userId &&
    !search.has(VIEWER_PREVIEW_PARAM)
  );
}
