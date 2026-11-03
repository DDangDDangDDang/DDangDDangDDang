/**
 * 라이브 봇 방송의 녹화 영상 재생 위치.
 * 봇 방송은 송출하는 사람이 없어 시청 화면이 영상을 직접 반복 재생한다. 방송 시작 시각부터 흐른 시간을 영상 길이로 나눈 나머지 위치에서 틀면
 * 누가 언제 들어와도(새로고침, 작은 플레이어, 홈 미리보기) 같은 장면을 본다.
 * 길이를 모르거나(0·NaN) 시작 시각이 이상하면 처음(0초)부터 튼다.
 */
export function botVideoPosition(startedAt: string | undefined, nowMs: number, durationSeconds: number): number {
  if (!startedAt || !Number.isFinite(durationSeconds) || durationSeconds <= 0) return 0;
  const started = new Date(startedAt).getTime();
  if (Number.isNaN(started)) return 0;
  const elapsed = Math.max(0, (nowMs - started) / 1000);
  return elapsed % durationSeconds;
}

/** 라이브 봇 녹화 영상으로 받는 형식 (서버 LiveBotService.VIDEO_TYPES와 같게) */
export const LIVE_BOT_VIDEO_TYPES = ['video/mp4', 'video/webm'];
/** 라이브 봇 녹화 영상 최대 크기 (서버 LiveBotService.MAX_VIDEO_BYTES와 같게) */
export const LIVE_BOT_VIDEO_MAX_BYTES = 500 * 1024 * 1024;

/** 올릴 수 없는 영상이면 이유, 올릴 수 있으면 undefined */
export function liveBotVideoProblem(file: { type: string; size: number }): string | undefined {
  if (!LIVE_BOT_VIDEO_TYPES.includes(file.type)) return 'MP4 또는 WEBM 영상만 올릴 수 있어요.';
  if (file.size > LIVE_BOT_VIDEO_MAX_BYTES) return '영상은 500MB까지 올릴 수 있어요.';
  if (file.size === 0) return '빈 파일은 올릴 수 없어요.';
  return undefined;
}
