import { api, call, uploadToSignedUrl, type Schema } from '@/api';

type LiveStream = Schema<'LiveStreamResponse'>;

/**
 * 판매자가 고른 이미지를 방송 카드 썸네일로 올린다: 업로드 URL 받기 → 저장소(S3)에 바로 올리기 → 서버에 확정.
 * 확정한 방송 정보(customThumbnailUrl 포함)를 돌려준다.
 */
export async function uploadCustomThumbnail(liveStreamId: number, file: File): Promise<LiveStream | undefined> {
  const params = { path: { liveStreamId } };
  const signed = await call(
    api.POST('/api/v1/live-streams/{liveStreamId}/custom-thumbnail-uploads', { params, body: { contentType: file.type, contentLength: file.size } }),
  );
  if (!signed?.uploadUrl || !signed.thumbnailKey) throw new Error('업로드 URL을 받지 못했어요.');
  await uploadToSignedUrl(signed.uploadUrl, file);
  return call(api.PUT('/api/v1/live-streams/{liveStreamId}/custom-thumbnail', { params, body: { thumbnailKey: signed.thumbnailKey } }));
}

/** 직접 올린 썸네일을 지운다. 방송 카드는 송출 화면 자동 캡처로 돌아간다. */
export function deleteCustomThumbnail(liveStreamId: number): Promise<LiveStream | undefined> {
  return call(api.DELETE('/api/v1/live-streams/{liveStreamId}/custom-thumbnail', { params: { path: { liveStreamId } } }));
}
