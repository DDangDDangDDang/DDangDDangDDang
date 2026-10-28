import { Sparkles, TrendingUp } from 'lucide-react';
import { useEffect, useRef, type CSSProperties } from 'react';
import type { ChatMessage } from '@/api';
import { bidIncreasePercent, useAuctionEffects, type AuctionEffect, type BidTier } from '@/lib/auction-effects';
import { playBidCoins, playGavel } from '@/lib/auction-sound';
import { OverlayItem } from '@/components/live/OverlayItem';
import { cn } from '@/lib/cn';
import { formatPoint } from '@/lib/format';
import { DEFAULT_OVERLAY_LAYOUT, type OverlayPosition } from '@/lib/overlay-layout';

/**
 * 방송 화면 위 입찰·낙찰 이펙트. 시청 화면·판매자 스튜디오·작은 방송 창에서 그 화면의 채팅 메시지를 넘겨 쓴다.
 * - 입찰: 영상 위쪽 가운데(왼쪽 위 LIVE·시청자 수 배지 아래)에 "누구님이 ‘상품’ 상품을 얼마에 입찰했습니다" + 입찰표를 흔드는 사람 + 동전 짤랑 소리.
 *   직전 현재가보다 10%·30%·50% 이상 올린 입찰은 동전이 많아지고, 더 빨리 흔들고, 금색으로 빛나며 소리도 화려하다.
 * - 낙찰: 영상 가운데에 "‘상품’ 상품이 누구님에게 얼마에 낙찰되었습니다" + 경매 망치가 땅땅땅 내리치는 애니메이션과 소리.
 * 여러 개면 차례로 보여 준다. 움직임을 줄이도록 설정한 사용자에게는 흔들기·떨어지는 동전을 보여 주지 않는다.
 * 방송 화면 배치 영역(OVERLAY_AREA_CLASS)을 덮는 자리(absolute inset-0)에 두면 판매자가 정한 위치(bidPosition·soldPosition)에 놓는다.
 * 정하지 않으면 입찰은 위쪽 가운데, 낙찰은 가운데. compact면(작은 방송 창) 둘 다 아래쪽에 작게.
 */
export function AuctionEffects({
  messages,
  compact = false,
  sound = false,
  bidPosition = DEFAULT_OVERLAY_LAYOUT.bid,
  soldPosition = DEFAULT_OVERLAY_LAYOUT.sold,
  className,
}: {
  messages: ChatMessage[];
  compact?: boolean;
  /** 이펙트가 뜰 때 효과음을 낼지 (시청자는 방송 소리를 켰을 때만, 판매자는 스튜디오 설정) */
  sound?: boolean;
  bidPosition?: OverlayPosition;
  soldPosition?: OverlayPosition;
  className?: string;
}) {
  const effect = useAuctionEffects(messages);
  // 이펙트가 하나 뜰 때마다 한 번 효과음을 낸다. 보여 주는 동안 sound를 바꿔도 다시 울리지 않도록 이펙트가 바뀔 때만 실행한다.
  const soundRef = useRef(sound);
  useEffect(() => {
    soundRef.current = sound;
  }, [sound]);
  const effectId = effect?.id;
  const effectSound = effect ? (effect.kind === 'SOLD' ? 'SOLD' : effect.tier) : undefined;
  useEffect(() => {
    if (!effectId || !effectSound || !soundRef.current) return;
    if (effectSound === 'SOLD') playGavel();
    else playBidCoins(effectSound);
  }, [effectId, effectSound]);

  return (
    <div aria-live="polite" className={cn('pointer-events-none', className)}>
      {effect &&
        (compact ? (
          <div className="absolute inset-x-0 bottom-2 flex justify-center px-2">
            {effect.kind === 'SOLD' ? <SoldNotice key={effect.id} effect={effect} compact /> : <BidNotice key={effect.id} effect={effect} compact />}
          </div>
        ) : (
          <OverlayItem position={effect.kind === 'SOLD' ? soldPosition : bidPosition}>
            {effect.kind === 'SOLD' ? <SoldNotice key={effect.id} effect={effect} compact={false} /> : <BidNotice key={effect.id} effect={effect} compact={false} />}
          </OverlayItem>
        ))}
    </div>
  );
}

/** 단계별 입찰 안내 모양: 흔드는 빠르기(ms), 떨어지는 동전 수, 바탕·테두리 */
const BID_LOOK: Record<BidTier, { wave: number; coins: number; card: string }> = {
  normal: { wave: 560, coins: 0, card: 'bg-stage/85 ring-1 ring-neutral-200' },
  up10: { wave: 440, coins: 6, card: 'bg-stage/85 ring-2 ring-brand-500' },
  up30: { wave: 340, coins: 10, card: 'bg-brand-800 ring-2 ring-gold-300' },
  up50: { wave: 260, coins: 16, card: 'animate-donation-glow bg-brand-800 ring-2 ring-gold-300' },
};

export function BidNotice({ effect, compact }: { effect: Extract<AuctionEffect, { kind: 'BID' }>; compact: boolean }) {
  const look = BID_LOOK[effect.tier];
  const percent = bidIncreasePercent(effect.amount, effect.previousAmount);
  const strong = effect.tier === 'up30' || effect.tier === 'up50';
  // 나타나는 움직임(pop)과 금색 반짝임(glow)은 둘 다 animation이라 한 요소에 두면 하나가 지워진다. 바깥·안쪽으로 나눈다.
  return (
    <div className={cn('animate-donation-pop', compact ? 'max-w-full' : 'max-w-[520px]')}>
      <div
        role="status"
        data-tier={effect.tier}
        className={cn('relative flex items-center gap-3 rounded-lg text-white shadow-md', compact ? 'px-3 py-2' : 'px-4 py-3', look.card)}
      >
        {!compact && <CoinRain count={look.coins} />}
        <BidderFigure waveMs={look.wave} hop={strong} gold={strong} className={cn('relative z-10 shrink-0', compact ? 'size-8' : 'size-14')} />
        <BidText effect={effect} compact={compact} strong={strong} percent={percent} />
      </div>
    </div>
  );
}

function BidText({
  effect,
  compact,
  strong,
  percent,
}: {
  effect: Extract<AuctionEffect, { kind: 'BID' }>;
  compact: boolean;
  strong: boolean;
  percent?: number;
}) {
  return (
    <p className={cn('relative z-10 min-w-0 leading-snug', compact ? 'text-label' : 'text-body-lg')}>
      <strong className="font-bold">{effect.nickname}</strong>님이 <span className="font-semibold">‘{effect.productName}’</span> 상품을{' '}
      <strong className={cn('num font-bold', strong ? 'text-gold-300' : 'text-brand-300')}>{formatPoint(effect.amount)}</strong>에 입찰했습니다.
      {percent !== undefined && effect.tier !== 'normal' && (
        <span
          className={cn(
            'ml-1.5 inline-flex items-center gap-0.5 rounded-sm px-1.5 align-[2px] text-caption font-bold',
            strong ? 'bg-gold-300 text-black' : 'bg-white/15 text-white',
          )}
        >
          <TrendingUp className="size-3" aria-hidden="true" />
          <span className="num">{percent}%</span> 올림
        </span>
      )}
      {effect.tier === 'up50' && <Sparkles className="ml-1 inline size-4 align-[-2px] text-gold-300" aria-hidden="true" />}
    </p>
  );
}

export function SoldNotice({ effect, compact }: { effect: Extract<AuctionEffect, { kind: 'SOLD' }>; compact: boolean }) {
  return (
    <div className="animate-donation-pop">
      <div
        role="status"
        className={cn(
          'animate-sold-shake relative flex items-center gap-4 rounded-lg bg-stage/90 text-white shadow-md ring-2 ring-gold-300 motion-reduce:animate-none',
          compact ? 'gap-2 px-3 py-2' : 'max-w-[560px] px-5 py-4',
        )}
      >
        {/* 망치를 다 내리친 뒤 축하 동전이 쏟아진다. */}
        {!compact && <CoinRain count={18} delayMs={1000} />}
        <Gavel className={cn('relative z-10 shrink-0', compact ? 'size-9' : 'size-20')} />
        <div className="relative z-10 flex min-w-0 flex-col gap-0.5">
          <span className={cn('font-bold tracking-wide text-gold-300', compact ? 'text-caption' : 'text-label')}>낙찰</span>
          <p className={cn('leading-snug', compact ? 'text-label' : 'text-title-md')}>
            <span className="font-semibold">‘{effect.productName}’</span> 상품이 <strong className="font-bold">{effect.nickname}</strong>님에게{' '}
            <strong className="num font-bold text-gold-300">{formatPoint(effect.amount)}</strong>에 낙찰되었습니다.
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * 입찰표를 든 사람. 입찰표를 든 팔이 어깨를 축으로 흔들린다. (장식이라 화면 읽기 프로그램에는 숨긴다)
 * hop이면 제자리에서 들썩이고, gold면 입찰표가 금색이다.
 */
function BidderFigure({ waveMs, hop, gold, className }: { waveMs: number; hop: boolean; gold: boolean; className?: string }) {
  const arm: CSSProperties = { transformBox: 'view-box', transformOrigin: '27px 33px', animationDuration: `${waveMs}ms` };
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" data-figure="bidder" className={cn('overflow-visible', className)}>
      <g className={cn(hop && 'animate-bid-hop motion-reduce:animate-none')} style={{ animationDuration: `${waveMs * 2}ms` }}>
        {/* 머리·몸 */}
        <circle cx="22" cy="20" r="7" className="fill-white" />
        <path d="M10 56 C10 38 14 31 22 31 C30 31 34 38 34 56 Z" className="fill-white/90" />
        {/* 입찰표를 든 팔 */}
        <g className="animate-bid-wave motion-reduce:animate-none" style={arm}>
          <path d="M27 33 L41 21" className="stroke-white" strokeWidth="5" strokeLinecap="round" />
          <path d="M41 21 L46 14" className="stroke-neutral-400" strokeWidth="3" strokeLinecap="round" />
          <circle cx="49" cy="10" r="9" className={gold ? 'fill-gold-300' : 'fill-brand-300'} />
          <text x="49" y="14" textAnchor="middle" fontSize="11" className="fill-black font-bold">
            P
          </text>
        </g>
      </g>
    </svg>
  );
}

/**
 * 경매 망치. 손잡이 끝을 축으로 세 번 내리쳐 받침에 닿고, 닿을 때마다 충격선이 퍼진다. (장식이라 화면 읽기 프로그램에는 숨긴다)
 */
function Gavel({ className }: { className?: string }) {
  const swing: CSSProperties = { transformBox: 'view-box', transformOrigin: '12px 20px' };
  const impact: CSSProperties = { transformBox: 'view-box', transformOrigin: '50px 44px' };
  return (
    // 들어 올린 망치 머리가 위로 16만큼 나가므로 그만큼 위를 넓혀 둔다.
    <svg viewBox="0 -16 76 80" aria-hidden="true" data-figure="gavel" className={cn('overflow-visible', className)}>
      {/* 받침 */}
      <rect x="34" y="46" width="32" height="7" rx="2" className="fill-brand-300" />
      <rect x="38" y="53" width="24" height="5" rx="1.5" className="fill-brand-500" />
      {/* 충격선 */}
      <g className="animate-gavel-impact motion-reduce:hidden" style={impact}>
        <path d="M30 40 L24 36 M70 40 L76 36 M50 36 L50 30" className="stroke-gold-300" strokeWidth="2.5" strokeLinecap="round" />
      </g>
      {/* 망치: 손잡이 + 머리 */}
      <g className="animate-gavel-strike motion-reduce:animate-none" style={swing}>
        <rect x="10" y="18" width="40" height="5" rx="2.5" className="fill-gold-300" />
        <rect x="44" y="7" width="14" height="28" rx="3" className="fill-white" />
        <rect x="44" y="13" width="14" height="3" className="fill-neutral-400" />
        <rect x="44" y="26" width="14" height="3" className="fill-neutral-400" />
      </g>
    </svg>
  );
}

/** 안내 위쪽에서 동전이 돌며 떨어진다. 자리·늦게 떨어지는 정도는 순서로 정해 매번 같게 그린다. (장식) */
function CoinRain({ count, delayMs = 0 }: { count: number; delayMs?: number }) {
  if (count === 0) return null;
  return (
    <span aria-hidden="true" className="absolute inset-x-0 top-0 z-20 motion-reduce:hidden">
      {Array.from({ length: count }, (_, i) => {
        const style = {
          '--coin-x': `${Math.round(((i * 61) % 100) - 50) * 4.6}px`,
          '--coin-fall': `${70 + ((i * 37) % 50)}px`,
          animationDelay: `${delayMs + ((i * 83) % 600)}ms`,
          animationDuration: `${1100 + ((i * 47) % 500)}ms`,
        } as CSSProperties;
        return (
          <span
            key={i}
            data-coin=""
            style={style}
            className="animate-coin-drop absolute top-0 left-1/2 size-4 rounded-full border-2 border-brand-500 bg-gold-300"
          />
        );
      })}
    </span>
  );
}
