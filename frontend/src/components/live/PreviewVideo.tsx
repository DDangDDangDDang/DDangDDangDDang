import type { RemoteTrack } from 'livekit-client';
import { useState, type ReactNode } from 'react';
import { BotVideo } from '@/components/watch/BotVideo';
import { TrackVideo } from '@/components/watch/tracks';
import { cn } from '@/lib/cn';
import type { BotVideoSource } from './use-live-preview';

/**
 * 썸네일 위에 겹쳐 그리는 미리보기 영상. 첫 화면이 나오면 서서히 보여 줘서, 접속하는 동안의 검은 화면 대신 썸네일이 보이게 한다.
 * 라이브 봇 방송(bot)은 LiveKit 트랙 대신 녹화 영상을 시작 시각에 맞춰 튼다. 소리는 bot에만 쓰고(muted), 사람 방송 소리는 따로 재생한다.
 * 장식용이므로 스크린 리더에는 숨긴다. (카드 링크 이름으로 방송을 알 수 있음)
 */
export function PreviewVideo({ track, bot, muted = true }: { track?: RemoteTrack; bot?: BotVideoSource; muted?: boolean }) {
  if (bot) {
    return (
      <PreviewFrame key={bot.url}>
        {(onPlaying) => <BotVideo url={bot.url} startedAt={bot.startedAt} muted={muted} onPlaying={onPlaying} />}
      </PreviewFrame>
    );
  }
  if (!track) return null;
  // 트랙이 바뀌면 다시 첫 화면을 기다린다.
  return <PreviewFrame key={track.sid}>{(onPlaying) => <TrackVideo track={track} onPlaying={onPlaying} />}</PreviewFrame>;
}

function PreviewFrame({ children }: { children: (onPlaying: () => void) => ReactNode }) {
  const [playing, setPlaying] = useState(false);
  return (
    <div
      aria-hidden="true"
      data-testid="live-preview"
      data-playing={playing}
      className={cn('absolute inset-0 bg-stage transition-opacity duration-300', playing ? 'opacity-100' : 'opacity-0')}
    >
      {children(() => setPlaying(true))}
    </div>
  );
}
