import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChatMessage } from '@/api';
import { bidIncreasePercent, bidTier, enqueueAuctionEffect, MAX_PENDING_BIDS, toAuctionEffect, useAuctionEffects, type AuctionEffect } from './auction-effects';

const bid = (id: string, amount: number, previousAmount: number, auctionId = 1): ChatMessage => ({
  messageId: id,
  type: 'BID',
  liveStreamId: 1,
  senderId: 3,
  nickname: '필름팬',
  content: `필름팬님이 ‘카메라’ 상품을 ${amount}P에 입찰했습니다.`,
  sentAt: '2026-10-24T12:00:00',
  amount,
  previousAmount,
  auctionId,
  productName: '카메라',
});

const sold = (id: string, amount: number, auctionId = 1): ChatMessage => ({ ...bid(id, amount, 0, auctionId), type: 'AUCTION_SOLD', previousAmount: null });

const effect = (message: ChatMessage) => toAuctionEffect(message) as AuctionEffect;

describe('bidTier', () => {
  it('직전 현재가보다 10%·30%·50% 이상 올렸는지로 단계를 정한다', () => {
    expect(bidTier(10_900, 10_000)).toBe('normal');
    expect(bidTier(11_000, 10_000)).toBe('up10');
    expect(bidTier(12_999, 10_000)).toBe('up10');
    expect(bidTier(13_000, 10_000)).toBe('up30');
    expect(bidTier(15_000, 10_000)).toBe('up50');
    expect(bidTier(40_000, 10_000)).toBe('up50');
  });

  it('직전 현재가를 모르면 기본 단계', () => {
    expect(bidTier(15_000, null)).toBe('normal');
    expect(bidTier(15_000, 0)).toBe('normal');
  });
});

describe('bidIncreasePercent', () => {
  it('오른 비율을 내림한 정수로, 알 수 없거나 오르지 않았으면 없음', () => {
    expect(bidIncreasePercent(13_333, 10_000)).toBe(33);
    expect(bidIncreasePercent(10_500, 10_000)).toBe(5);
    expect(bidIncreasePercent(10_000, 10_000)).toBeUndefined();
    expect(bidIncreasePercent(10_000, undefined)).toBeUndefined();
  });
});

describe('toAuctionEffect', () => {
  it('입찰·낙찰 메시지만 이펙트로 바꾼다', () => {
    expect(toAuctionEffect(bid('a', 15_000, 10_000))).toMatchObject({ kind: 'BID', nickname: '필름팬', productName: '카메라', amount: 15_000, tier: 'up50' });
    expect(toAuctionEffect(sold('b', 20_000))).toMatchObject({ kind: 'SOLD', amount: 20_000 });
    expect(toAuctionEffect({ ...bid('c', 1, 1), type: 'CHAT' })).toBeUndefined();
  });
});

describe('enqueueAuctionEffect', () => {
  it('기다리는 입찰 안내가 많으면 오래된 입찰부터 건너뛴다', () => {
    let queue: AuctionEffect[] = [];
    for (let i = 1; i <= MAX_PENDING_BIDS + 2; i += 1) queue = enqueueAuctionEffect(queue, effect(bid(String(i), 10_000 + i, 10_000)));
    expect(queue.map((item) => item.id)).toEqual(['3', '4', '5']);
  });

  it('낙찰이 오면 같은 경매의 기다리던 입찰은 빼고, 다른 경매의 입찰보다 먼저 보여 준다', () => {
    const queue = [effect(bid('a', 11_000, 10_000, 1)), effect(bid('b', 5_500, 5_000, 2))];
    expect(enqueueAuctionEffect(queue, effect(sold('c', 11_000, 1))).map((item) => item.id)).toEqual(['c', 'b']);
  });

  it('낙찰이 여러 개면 온 순서대로, 기다리던 입찰은 그 뒤에', () => {
    const queue = [effect(sold('s1', 11_000, 1)), effect(bid('b', 5_500, 5_000, 2))];
    expect(enqueueAuctionEffect(queue, effect(sold('s2', 9_000, 3))).map((item) => item.id)).toEqual(['s1', 's2', 'b']);
  });
});

describe('useAuctionEffects', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('처음 있던 메시지는 보여 주지 않고, 새 입찰·낙찰을 차례로 보여 준다', () => {
    const { result, rerender } = renderHook(({ messages }) => useAuctionEffects(messages, { bid: 1_000, sold: 2_000 }), {
      initialProps: { messages: [bid('old', 11_000, 10_000)] },
    });
    expect(result.current).toBeUndefined();

    rerender({ messages: [bid('old', 11_000, 10_000), bid('a', 12_000, 11_000, 2)] });
    expect(result.current?.id).toBe('a');
    act(() => vi.advanceTimersByTime(1_000));
    expect(result.current).toBeUndefined();
  });

  it('입찰 안내 중에 낙찰이 오면 입찰 안내를 바로 끝내고 낙찰을 보여 준다', () => {
    const { result, rerender } = renderHook(({ messages }) => useAuctionEffects(messages, { bid: 1_000, sold: 2_000 }), {
      initialProps: { messages: [] as ChatMessage[] },
    });
    rerender({ messages: [bid('a', 11_000, 10_000)] });
    expect(result.current?.kind).toBe('BID');

    rerender({ messages: [bid('a', 11_000, 10_000), sold('b', 11_000)] });
    expect(result.current?.kind).toBe('SOLD');
    act(() => vi.advanceTimersByTime(1_500));
    expect(result.current?.kind).toBe('SOLD');
    act(() => vi.advanceTimersByTime(500));
    expect(result.current).toBeUndefined();
  });
});
