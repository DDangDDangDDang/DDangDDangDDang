import type { RemoteTrack } from 'livekit-client';
import { useEffect, useRef } from 'react';
import { cn } from '@/lib/cn';

/** 방송 영상 트랙을 그린다. (시청 화면·작은 플레이어가 같이 쓴다) */
export function TrackVideo({ track, className, onPlaying }: { track: RemoteTrack; className?: string; onPlaying?: () => void }) {
  const ref = useRef<HTMLVideoElement>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [track]);
  // 소리는 별도 오디오 트랙으로 재생하므로 영상은 항상 음소거한다. (자동 재생 허용)
  return <video ref={ref} className={cn('absolute inset-0 size-full object-contain', className)} autoPlay playsInline muted onPlaying={onPlaying} />;
}

/** 방송 소리 트랙을 재생한다. */
export function TrackAudio({ track, muted }: { track: RemoteTrack; muted: boolean }) {
  const ref = useRef<HTMLAudioElement>(null);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    track.attach(element);
    return () => {
      track.detach(element);
    };
  }, [track]);
  // attach()가 요소의 muted 값을 바꿀 수 있으므로, 연결한 뒤와 음소거를 바꿀 때마다 화면의 값을 다시 적용한다.
  useEffect(() => {
    if (ref.current) ref.current.muted = muted;
  }, [track, muted]);
  // 처음 그릴 때부터 음소거 상태로 둔다. (attach()가 연결하면서 바로 재생을 시작하므로)
  return <audio ref={ref} autoPlay muted={muted} />;
}
