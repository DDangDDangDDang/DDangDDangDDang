import { useEffect, useRef } from 'react';
import { botVideoPosition } from '@/lib/bot-video';
import { cn } from '@/lib/cn';

type Props = {
  /** 녹화 영상 주소 (LiveStreamResponse·LiveStreamCard의 videoUrl) */
  url: string;
  /** 방송 시작 시각. 이 시각부터 흐른 만큼 넘긴 위치에서 튼다. */
  startedAt?: string;
  muted: boolean;
  volume?: number;
  className?: string;
  onPlaying?: () => void;
};

/**
 * 라이브 봇 방송 영상. LiveKit 트랙 대신 녹화 영상을 반복 재생한다. (시청 화면·작은 플레이어·홈 미리보기가 같이 쓴다)
 * 방송 시작 시각에 맞춘 위치에서 틀어 누가 언제 들어와도 같은 장면을 본다. 탭을 다시 보면 위치를 다시 맞춘다. (숨긴 탭은 브라우저가 재생을 늦춤)
 * 브라우저는 소리 있는 자동 재생을 막으므로 소리를 켜지 못하면 음소거로라도 튼다.
 */
export function BotVideo({ url, startedAt, muted, volume = 1, className, onPlaying }: Props) {
  const ref = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const sync = () => {
      element.currentTime = botVideoPosition(startedAt, Date.now(), element.duration);
      element.play().catch(() => {
        element.muted = true;
        void element.play().catch(() => undefined);
      });
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible' && element.readyState > 0) sync();
    };
    element.addEventListener('loadedmetadata', sync);
    document.addEventListener('visibilitychange', onVisible);
    if (element.readyState > 0) sync();
    return () => {
      element.removeEventListener('loadedmetadata', sync);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [url, startedAt]);

  useEffect(() => {
    if (ref.current) ref.current.muted = muted;
  }, [muted]);
  useEffect(() => {
    if (ref.current) ref.current.volume = volume;
  }, [volume]);

  return (
    <video
      ref={ref}
      src={url}
      className={cn('absolute inset-0 size-full object-contain', className)}
      loop
      playsInline
      muted={muted}
      preload="auto"
      onPlaying={onPlaying}
    />
  );
}
