import type { RemoteTrack } from 'livekit-client';
import { useState } from 'react';
import { TrackVideo } from '@/components/watch/tracks';
import { cn } from '@/lib/cn';

/**
 * 썸네일 위에 겹쳐 그리는 미리보기 영상. 첫 화면이 나오면 서서히 보여 줘서, 접속하는 동안의 검은 화면 대신 썸네일이 보이게 한다.
 * 장식용이므로 스크린 리더에는 숨긴다. (카드 링크 이름으로 방송을 알 수 있음)
 */
export function PreviewVideo({ track }: { track?: RemoteTrack }) {
  if (!track) return null;
  // 트랙이 바뀌면 다시 첫 화면을 기다린다.
  return <PreviewTrack key={track.sid} track={track} />;
}

function PreviewTrack({ track }: { track: RemoteTrack }) {
  const [playing, setPlaying] = useState(false);
  return (
    <div
      aria-hidden="true"
      data-testid="live-preview"
      data-playing={playing}
      className={cn('absolute inset-0 bg-stage transition-opacity duration-300', playing ? 'opacity-100' : 'opacity-0')}
    >
      <TrackVideo track={track} onPlaying={() => setPlaying(true)} />
    </div>
  );
}
