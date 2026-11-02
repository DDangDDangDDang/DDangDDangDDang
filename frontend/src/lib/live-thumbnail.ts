/**
 * 방송 카드 썸네일 (치지직처럼 지금 방송 화면).
 * 송출하는 판매자 탭이 이 간격으로 화면을 캡처해 올리고, 홈 화면도 같은 간격으로 방송 목록을 새로 불러온다.
 */
export const THUMBNAIL_INTERVAL_MS = 60_000;

/** 썸네일 폭(px). 카드에 그리기에 충분하고 파일이 작다. (서버는 1MB까지 받는다) */
const THUMBNAIL_WIDTH = 640;
const JPEG_QUALITY = 0.75;
/** 캡처할 영상의 첫 화면을 기다리는 최대 시간 */
const FRAME_WAIT_MS = 3000;

/** 영상 크기에 맞춘 썸네일 크기. 폭은 THUMBNAIL_WIDTH를 넘지 않고 비율을 지킨다. (영상 크기를 모르면 undefined) */
export function thumbnailSize(videoWidth: number, videoHeight: number): { width: number; height: number } | undefined {
  if (videoWidth <= 0 || videoHeight <= 0) return undefined;
  const width = Math.min(videoWidth, THUMBNAIL_WIDTH);
  return { width, height: Math.round((videoHeight * width) / videoWidth) };
}

/** 영상 트랙의 지금 화면을 JPG로 만든다. 화면이 아직 없으면 undefined */
export async function captureJpeg(track: MediaStreamTrack): Promise<Blob | undefined> {
  const video = document.createElement('video');
  video.muted = true;
  video.playsInline = true;
  video.srcObject = new MediaStream([track]);
  try {
    await video.play();
    const deadline = Date.now() + FRAME_WAIT_MS;
    while (video.videoWidth === 0 && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    const size = thumbnailSize(video.videoWidth, video.videoHeight);
    if (!size) return undefined;
    const canvas = document.createElement('canvas');
    canvas.width = size.width;
    canvas.height = size.height;
    canvas.getContext('2d')?.drawImage(video, 0, 0, size.width, size.height);
    return (await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY))) ?? undefined;
  } finally {
    video.pause();
    video.srcObject = null;
  }
}

/*
 * 판매자가 직접 올리는 방송 카드 썸네일. 서버(LiveStreamCustomThumbnailService)와 같은 규칙으로 올리기 전에 먼저 걸러 낸다.
 * 있으면 자동 캡처보다 먼저 카드에 보이고, 지우면 자동 캡처로 돌아간다.
 */

/** 서버가 받는 형식 (JPG, PNG, WEBP) */
export const CUSTOM_THUMBNAIL_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
export const CUSTOM_THUMBNAIL_MAX_BYTES = 2 * 1024 * 1024;

/** 올릴 수 없는 파일이면 이유, 올릴 수 있으면 undefined */
export function customThumbnailProblem(file: { type: string; size: number }): string | undefined {
  if (!(CUSTOM_THUMBNAIL_TYPES as readonly string[]).includes(file.type)) return 'JPG, PNG, WEBP 이미지만 올릴 수 있어요.';
  if (file.size === 0) return '빈 파일은 올릴 수 없어요.';
  if (file.size > CUSTOM_THUMBNAIL_MAX_BYTES) return '2MB 이하 이미지만 올릴 수 있어요.';
  return undefined;
}
