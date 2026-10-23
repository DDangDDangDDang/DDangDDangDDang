import { Gift, Sparkles, Star } from 'lucide-react';
import { useEffect, useRef, type CSSProperties } from 'react';
import type { ChatMessage } from '@/api';
import { cn } from '@/lib/cn';
import { donationTier, playDonationChime, type DonationTier } from '@/lib/donation-sound';
import { formatPoint } from '@/lib/format';
import { useDonationEffects } from '@/lib/use-donation-effects';

/**
 * 방송 화면 위 선물 이펙트: "누구님이 몇 P를 선물했어요" (+ 함께 보낸 한마디)
 * 시청 화면·판매자 스튜디오·작은 방송 창에서 그 화면의 채팅 메시지를 넘겨 쓴다. 여러 개면 차례로 보여 준다.
 * 안내가 나타날 때 별이 사방으로 튀어나온다. 선물이 클수록(1,000P·3,000P 이상) 별이 많고 멀리 퍼지며 효과음도 화려하다.
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
  const tier = effect ? donationTier(effect.amount) : 'small';
  return (
    <div aria-live="polite" className={cn('pointer-events-none', className)}>
      {effect && (
        <div
          key={effect.id}
          role="status"
          className={cn(
            'animate-donation-pop relative flex items-center gap-3 rounded-lg bg-brand-500 text-neutral-0 shadow-md',
            compact ? 'px-3 py-2' : 'max-w-[420px] px-4 py-3',
            tier === 'large' && 'ring-2 ring-brand-300',
          )}
        >
          <StarBurst tier={tier} compact={compact} />
          <span className={cn('relative flex shrink-0 items-center justify-center rounded-full bg-neutral-0/20', compact ? 'size-7' : 'size-10')}>
            <Gift className={compact ? 'size-4' : 'size-5'} aria-hidden="true" />
          </span>
          <span className="relative flex min-w-0 flex-col">
            <span className={cn('font-bold', compact ? 'text-label' : 'text-body-lg')}>
              {effect.nickname}님이 <span className="num">{formatPoint(effect.amount)}</span>를 선물했어요
              {tier === 'large' && <Sparkles className="ml-1 inline size-4 align-[-2px] text-brand-100" aria-hidden="true" />}
            </span>
            {effect.message && !compact && <span className="truncate text-body text-neutral-0/90">{effect.message}</span>}
          </span>
        </div>
      )}
    </div>
  );
}

/** 단계별 별 개수와 퍼지는 거리(px) */
const BURST: Record<DonationTier, { count: number; distance: number }> = {
  small: { count: 6, distance: 90 },
  medium: { count: 10, distance: 130 },
  large: { count: 18, distance: 180 },
};
const STAR_COLORS = ['text-brand-100', 'text-neutral-0', 'text-brand-300'];

/** 안내 가운데에서 별이 사방으로 튀어나왔다가 사라진다. (장식이라 화면 읽기 프로그램에는 숨긴다) */
function StarBurst({ tier, compact }: { tier: DonationTier; compact: boolean }) {
  const { count, distance } = BURST[tier];
  const reach = compact ? distance * 0.5 : distance;
  return (
    <span aria-hidden="true" className="absolute top-1/2 left-1/2 motion-reduce:hidden">
      {Array.from({ length: count }, (_, i) => {
        // 안내가 영상 위쪽에 떠서 위로 튀면 잘리므로, 옆과 아래(-25도~205도)로 고르게 퍼지게 한다. 조금씩 어긋나게 해서 자연스럽게.
        const angle = (-25 + (230 * i) / Math.max(1, count - 1)) * (Math.PI / 180) + (i % 2 === 0 ? 0.08 : -0.06);
        const length = reach * (0.65 + ((i * 37) % 10) / 28);
        const style = {
          '--star-x': `${Math.round(Math.cos(angle) * length)}px`,
          '--star-y': `${Math.round(Math.sin(angle) * length)}px`,
          animationDelay: `${(i % 3) * 60}ms`,
        } as CSSProperties;
        return (
          <Star
            key={i}
            style={style}
            className={cn(
              'animate-donation-star absolute fill-current',
              compact ? 'size-3' : i % 3 === 0 ? 'size-5' : 'size-4',
              STAR_COLORS[i % STAR_COLORS.length],
            )}
          />
        );
      })}
    </span>
  );
}
