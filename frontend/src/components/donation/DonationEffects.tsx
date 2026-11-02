import { Gift, Sparkle, Sparkles, Star } from 'lucide-react';
import { useEffect, useRef, type CSSProperties } from 'react';
import type { ChatMessage } from '@/api';
import { cn } from '@/lib/cn';
import { donationTier, playDonationChime, type DonationTier } from '@/lib/donation-sound';
import { formatMileage } from '@/lib/format';
import { useDonationEffects, type DonationEffect } from '@/lib/use-donation-effects';

/**
 * 방송 화면 위 선물 이펙트: "누구님이 몇 M을 선물했어요" (+ 함께 보낸 한마디)
 * 시청 화면·판매자 스튜디오·작은 방송 창에서 그 화면의 채팅 메시지를 넘겨 쓴다. 여러 개면 차례로 보여 준다.
 * 안내가 나타날 때 별이 사방으로 튀어나온다. 선물이 클수록(1,000M·3,000M 이상) 별이 많고 멀리 퍼지며 효과음도 화려하다.
 * 움직임을 줄이도록 설정한 사용자에게는 별을 보여 주지 않는다.
 */
export function DonationEffects({
  messages,
  compact = false,
  sound = false,
  className,
}: {
  messages: ChatMessage[];
  compact?: boolean;
  /** 이펙트가 뜰 때 효과음을 낼지 (시청자는 방송 소리를 켰을 때만, 판매자는 스튜디오 설정) */
  sound?: boolean;
  className?: string;
}) {
  const effect = useDonationEffects(messages);
  // 이펙트가 하나 뜰 때마다 한 번 효과음을 낸다. (sound가 꺼져 있으면 소리 없이)
  // 같은 이펙트를 보여 주는 동안 sound를 켜고 꺼도 다시 울리지 않도록, 이펙트가 바뀔 때만 실행한다.
  const soundRef = useRef(sound);
  useEffect(() => {
    soundRef.current = sound;
  }, [sound]);
  const effectId = effect?.id;
  const effectAmount = effect?.amount ?? 0;
  useEffect(() => {
    if (effectId && soundRef.current) playDonationChime(effectAmount);
  }, [effectId, effectAmount]);
  return (
    <div aria-live="polite" className={cn('pointer-events-none', className)}>
      {effect && <DonationNotice key={effect.id} effect={effect} compact={compact} />}
    </div>
  );
}

/** 선물 안내 하나. (스튜디오 화면 배치 편집에서 견본으로도 쓴다) */
export function DonationNotice({ effect, compact = false }: { effect: DonationEffect; compact?: boolean }) {
  const tier = donationTier(effect.amount);
  return (
    <div
      role="status"
      className={cn(
        'animate-donation-pop relative flex items-center gap-3 rounded-lg bg-brand-800 text-white shadow-md',
        compact ? 'px-3 py-2' : 'max-w-[420px] px-4 py-3',
        // 1,000M 이상은 테두리, 3,000M 이상은 금색 테두리가 두 번 반짝인다.
        tier === 'medium' && 'ring-1 ring-brand-300',
        tier === 'large' && 'animate-donation-glow ring-2 ring-gold-300',
      )}
    >
      <StarBurst tier={tier} compact={compact} />
      <span className={cn('relative z-10 flex shrink-0 items-center justify-center rounded-full bg-neutral-0/20', compact ? 'size-7' : 'size-10')}>
        <Gift className={compact ? 'size-4' : 'size-5'} aria-hidden="true" />
      </span>
      <span className="relative z-10 flex min-w-0 flex-col">
        <span className={cn('font-bold', compact ? 'text-label' : 'text-body-lg')}>
          {effect.nickname}님이 <span className="num">{formatMileage(effect.amount)}</span>을 선물했어요
          {tier === 'large' && <Sparkles className="ml-1 inline size-4 align-[-2px] text-gold-300" aria-hidden="true" />}
        </span>
        {effect.message && !compact && <span className="truncate text-body text-white/90">{effect.message}</span>}
      </span>
    </div>
  );
}

type Particle = { shape: 'star' | 'sparkle'; size: string; color: string };
const FALLBACK_PARTICLE: Particle = { shape: 'star', size: 'size-3', color: 'text-white' };

/**
 * 단계별 별 이펙트. 선물이 클수록 많고·크고·멀리·오래 퍼지고, 3,000M 이상은 금색 별이 두 번 터진다.
 * - waves: 터지는 횟수 (두 번째는 조금 늦게, 더 멀리)
 * - count: 한 번에 튀어나오는 수, distance: 퍼지는 거리(px), duration: 한 개가 날아가는 시간(ms)
 * - particles: 모양·크기·색을 차례로 돌려 쓴다
 */
const BURST: Record<DonationTier, { waves: number; count: number; distance: number; duration: number; particles: Particle[] }> = {
  small: {
    waves: 1,
    count: 6,
    distance: 80,
    duration: 900,
    particles: [
      { shape: 'star', size: 'size-3', color: 'text-white' },
      { shape: 'star', size: 'size-3', color: 'text-brand-100' },
    ],
  },
  medium: {
    waves: 1,
    count: 12,
    distance: 130,
    duration: 1100,
    particles: [
      { shape: 'star', size: 'size-5', color: 'text-brand-100' },
      { shape: 'sparkle', size: 'size-4', color: 'text-white' },
      { shape: 'star', size: 'size-4', color: 'text-brand-300' },
    ],
  },
  large: {
    waves: 2,
    count: 12,
    distance: 170,
    duration: 1400,
    particles: [
      { shape: 'star', size: 'size-6', color: 'text-gold-300' },
      { shape: 'sparkle', size: 'size-5', color: 'text-white' },
      { shape: 'star', size: 'size-5', color: 'text-brand-300' },
      { shape: 'sparkle', size: 'size-4', color: 'text-gold-300' },
    ],
  },
};

/** 안내 가운데에서 별이 튀어나왔다가 사라진다. (장식이라 화면 읽기 프로그램에는 숨긴다) */
function StarBurst({ tier, compact }: { tier: DonationTier; compact: boolean }) {
  const { waves, count, distance, duration, particles } = BURST[tier];
  return (
    <span aria-hidden="true" className="absolute top-1/2 left-1/2 z-0 motion-reduce:hidden">
      {Array.from({ length: waves * count }, (_, n) => {
        const wave = Math.floor(n / count);
        const i = n % count;
        // 안내가 영상 위쪽에 떠서 위로 튀면 잘리므로, 옆과 아래(-25도~205도)로 고르게 퍼지게 한다.
        // 두 번째 터짐은 첫 번째 사이사이로 어긋나게, 조금 늦게, 더 멀리 퍼진다.
        const offset = wave === 0 ? 0 : 0.5;
        const angle = (-25 + (230 * (i + offset)) / Math.max(1, count - 1 + offset)) * (Math.PI / 180) + (i % 2 === 0 ? 0.08 : -0.06);
        const reach = (compact ? distance * 0.5 : distance) * (wave === 0 ? 1 : 1.3);
        const length = reach * (0.65 + ((i * 37) % 10) / 28);
        const particle = particles[n % particles.length] ?? FALLBACK_PARTICLE;
        const style = {
          '--star-x': `${Math.round(Math.cos(angle) * length)}px`,
          '--star-y': `${Math.round(Math.sin(angle) * length)}px`,
          animationDuration: `${duration}ms`,
          animationDelay: `${wave * 350 + (i % 3) * 60}ms`,
        } as CSSProperties;
        const Shape = particle.shape === 'star' ? Star : Sparkle;
        return (
          <Shape
            key={n}
            data-particle={particle.shape}
            style={style}
            className={cn('animate-donation-star absolute fill-current', compact ? 'size-3' : particle.size, particle.color)}
          />
        );
      })}
    </span>
  );
}
