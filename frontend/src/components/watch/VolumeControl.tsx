import { Volume1, Volume2, VolumeX } from 'lucide-react';
import { cn } from '@/lib/cn';

type Props = {
  muted: boolean;
  /** 소리 크기 (0~1) */
  volume: number;
  onToggleSound: () => void;
  onVolumeChange: (volume: number) => void;
  /** stage: 영상 위(어두운 반투명 바탕), panel: 작은 플레이어 아래 줄 */
  tone?: 'stage' | 'panel';
};

/** 방송 소리 켜기/끄기 버튼 + 소리 크기 슬라이더. (시청 화면·작은 플레이어가 같이 쓴다) */
export function VolumeControl({ muted, volume, onToggleSound, onVolumeChange, tone = 'stage' }: Props) {
  const shown = muted ? 0 : volume;
  const label = muted ? '소리 켜기' : '소리 끄기';
  const Icon = shown === 0 ? VolumeX : shown < 0.5 ? Volume1 : Volume2;
  const stage = tone === 'stage';

  return (
    <div className={cn('flex items-center rounded-md', stage ? 'h-11 gap-1 bg-stage/70 pr-3 text-white' : 'h-8 gap-1.5 bg-neutral-50 pr-2.5 text-neutral-700')}>
      <button
        type="button"
        aria-label={label}
        title={label}
        onClick={onToggleSound}
        className={cn(
          'flex shrink-0 items-center justify-center rounded-md transition-colors',
          stage ? 'size-11 hover:bg-stage' : 'size-8 hover:bg-neutral-100',
        )}
      >
        <Icon className={stage ? 'size-5' : 'size-4'} aria-hidden="true" />
      </button>
      <input
        type="range"
        aria-label="소리 크기"
        min={0}
        max={100}
        step={1}
        value={Math.round(shown * 100)}
        aria-valuetext={`${Math.round(shown * 100)}%`}
        onChange={(event) => onVolumeChange(Number(event.target.value) / 100)}
        className={cn('cursor-pointer', stage ? 'w-24 accent-neutral-0' : 'w-20 accent-brand-500')}
      />
    </div>
  );
}
