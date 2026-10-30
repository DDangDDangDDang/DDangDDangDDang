import { useEffect } from 'react';
import { api, call, uploadToSignedUrl } from '@/api';
import { captureJpeg, THUMBNAIL_INTERVAL_MS } from '@/lib/live-thumbnail';

/** 송출을 시작하고 첫 썸네일을 찍기까지 기다리는 시간 (카메라 화면이 자리 잡을 때까지) */
export const FIRST_CAPTURE_DELAY_MS = 3000;

/**
 * 송출 중인 화면을 THUMBNAIL_INTERVAL_MS마다 캡처해 방송 카드 썸네일로 올린다. (치지직처럼)
 * liveStreamId·track이 있을 때만 돈다. 송출을 맡은 탭에서 송출이 연결됐을 때만 넘긴다. (BroadcastProvider)
 * 실패해도 다음 차례에 다시 올리면 되므로 화면에 알리지 않는다.
 */
export function useThumbnailCapture(liveStreamId: number | undefined, track: MediaStreamTrack | undefined) {
  useEffect(() => {
    if (liveStreamId === undefined || !track) return;
    let cancelled = false;
    const params = { path: { liveStreamId } };

    const upload = async () => {
      const image = await captureJpeg(track);
      if (cancelled || !image) return;
      const signed = await call(
        api.POST('/api/v1/live-streams/{liveStreamId}/thumbnail-uploads', { params, body: { contentType: image.type, contentLength: image.size } }),
      );
      if (cancelled || !signed?.uploadUrl || !signed.thumbnailKey) return;
      await uploadToSignedUrl(signed.uploadUrl, image);
      if (cancelled) return;
      await call(api.PUT('/api/v1/live-streams/{liveStreamId}/thumbnail', { params, body: { thumbnailKey: signed.thumbnailKey } }));
    };
    const capture = () => void upload().catch(() => undefined);

    const first = setTimeout(capture, FIRST_CAPTURE_DELAY_MS);
    const timer = setInterval(capture, THUMBNAIL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [liveStreamId, track]);
}
