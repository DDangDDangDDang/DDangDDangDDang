import { useEffect, useRef, useState } from 'react';
import type { ChatMessage } from '@/api';

/**
 * 입찰 이펙트 단계. 직전 현재가보다 얼마나 올려 입찰했는지로 정한다. 단계가 높을수록 효과음과 애니메이션이 화려하다.
 * - normal: 10% 미만
 * - up10: 10% 이상
 * - up30: 30% 이상
 * - up50: 50% 이상
 */
export type BidTier = 'normal' | 'up10' | 'up30' | 'up50';

const TIER_STEPS: { tier: BidTier; ratio: number }[] = [
  { tier: 'up50', ratio: 0.5 },
  { tier: 'up30', ratio: 0.3 },
  { tier: 'up10', ratio: 0.1 },
];

export function bidTier(amount: number, previousAmount: number | null | undefined): BidTier {
  if (!previousAmount || previousAmount <= 0) return 'normal';
  const ratio = (amount - previousAmount) / previousAmount;
  return TIER_STEPS.find((step) => ratio >= step.ratio)?.tier ?? 'normal';
}

/** 직전 현재가 대비 오른 비율(%)을 내림한 정수. 알 수 없으면 undefined */
export function bidIncreasePercent(amount: number, previousAmount: number | null | undefined): number | undefined {
  if (!previousAmount || previousAmount <= 0 || amount <= previousAmount) return undefined;
  return Math.floor(((amount - previousAmount) / previousAmount) * 100);
}

export type AuctionEffect =
  | { kind: 'BID'; id: string; auctionId?: number; nickname: string; productName: string; amount: number; previousAmount?: number; tier: BidTier }
  | { kind: 'SOLD'; id: string; auctionId?: number; nickname: string; productName: string; amount: number };

/** 입찰 안내 하나를 보여 주는 시간 */
export const BID_EFFECT_MS = 3_500;
/** 낙찰 안내를 보여 주는 시간 (망치를 세 번 내리치고 축하까지) */
export const SOLD_EFFECT_MS = 5_000;
/** 입찰이 몰리면 기다리는 입찰 안내를 이만큼만 남기고 오래된 것은 건너뛴다. (안내가 실제 입찰보다 한참 늦게 뜨지 않게) */
export const MAX_PENDING_BIDS = 3;

export function toAuctionEffect(message: ChatMessage): AuctionEffect | undefined {
  const base = {
    id: message.messageId,
    auctionId: message.auctionId ?? undefined,
    nickname: message.nickname || '알 수 없음',
    productName: message.productName || '상품',
    amount: message.amount ?? 0,
  };
  if (message.type === 'BID') {
    return { ...base, kind: 'BID', previousAmount: message.previousAmount ?? undefined, tier: bidTier(base.amount, message.previousAmount) };
  }
  if (message.type === 'AUCTION_SOLD') return { ...base, kind: 'SOLD' };
  return undefined;
}

/**
 * 새 안내를 기다리는 줄에 넣는다.
 * - 낙찰이 오면 같은 경매의 기다리던 입찰 안내는 뺀다. (이미 끝난 경매의 입찰을 뒤늦게 보여 주지 않게)
 * - 기다리는 입찰 안내가 MAX_PENDING_BIDS개를 넘으면 오래된 것부터 뺀다. 낙찰 안내는 빼지 않는다.
 */
export function enqueueAuctionEffect(queue: AuctionEffect[], effect: AuctionEffect): AuctionEffect[] {
  const kept = effect.kind === 'SOLD' ? queue.filter((item) => !(item.kind === 'BID' && item.auctionId === effect.auctionId)) : queue;
  const next = [...kept, effect];
  let extraBids = next.filter((item) => item.kind === 'BID').length - MAX_PENDING_BIDS;
  if (extraBids <= 0) return next;
  return next.filter((item) => {
    if (item.kind !== 'BID' || extraBids <= 0) return true;
    extraBids -= 1;
    return false;
  });
}

/**
 * 방송 채팅으로 온 입찰(BID)·낙찰(AUCTION_SOLD)을 하나씩 차례로 보여 준다. (방송 화면 이펙트)
 * - 화면을 처음 그릴 때 이미 있던 메시지는 다시 보여 주지 않는다.
 * - 입찰 안내를 보여 주는 중에 낙찰이 오면 입찰 안내를 바로 끝내고 낙찰을 보여 준다.
 */
export function useAuctionEffects(messages: ChatMessage[], durations = { bid: BID_EFFECT_MS, sold: SOLD_EFFECT_MS }): AuctionEffect | undefined {
  const seenRef = useRef<Set<string> | null>(null);
  const [queue, setQueue] = useState<AuctionEffect[]>([]);
  const [current, setCurrent] = useState<AuctionEffect>();

  useEffect(() => {
    if (seenRef.current === null) {
      seenRef.current = new Set(messages.map((message) => message.messageId));
      return;
    }
    const seen = seenRef.current;
    const fresh: AuctionEffect[] = [];
    for (const message of messages) {
      if (seen.has(message.messageId)) continue;
      seen.add(message.messageId);
      const effect = toAuctionEffect(message);
      if (effect) fresh.push(effect);
    }
    if (fresh.length === 0) return;
    setQueue((list) => fresh.reduce(enqueueAuctionEffect, list));
    if (fresh.some((effect) => effect.kind === 'SOLD')) setCurrent((shown) => (shown?.kind === 'BID' ? undefined : shown));
  }, [messages]);

  // 지금 보여 주는 것이 없으면 줄의 맨 앞을 꺼낸다.
  useEffect(() => {
    if (current || queue.length === 0) return;
    setCurrent(queue[0]);
    setQueue((list) => list.slice(1));
  }, [current, queue]);

  const { bid, sold } = durations;
  useEffect(() => {
    if (!current) return;
    const timer = setTimeout(() => setCurrent(undefined), current.kind === 'SOLD' ? sold : bid);
    return () => clearTimeout(timer);
  }, [current, bid, sold]);

  return current;
}
