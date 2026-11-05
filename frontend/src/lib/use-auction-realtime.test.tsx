import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { auctionKey, BID_REFRESH_DELAY_MS, useAuctionRealtime } from './use-auction-realtime';

const state = vi.hoisted(() => ({ onMessage: undefined as ((message: unknown) => void) | undefined }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => ({ accessToken: 't', user: { userId: 1, role: 'USER' } }),
    connectRealtime: () => ({
      subscribe: (_: string, handler: (message: unknown) => void) => {
        state.onMessage = handler;
      },
      close: () => undefined,
    }),
  };
});

const bid = (amount: number) => ({ auctionId: 7, bidderNickname: '땅*이', amount, bidCount: amount / 1000, endAt: '2026-11-03T12:00:00', extended: false });

function setup(myBidState?: string) {
  const client = new QueryClient();
  client.setQueryData(auctionKey(7), { id: 7, currentPrice: 10000, bidCount: 0 });
  if (myBidState) client.setQueryData([...auctionKey(7), 'my-bid'], { state: myBidState });
  const invalidate = vi.spyOn(client, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>;
  renderHook(() => useAuctionRealtime(7), { wrapper });
  const invalidated = () => invalidate.mock.calls.map(([filters]) => JSON.stringify(filters?.queryKey));
  return { client, invalidated };
}

describe('useAuctionRealtime', () => {
  afterEach(() => vi.useRealTimers());

  it('입찰 메시지는 현재가를 캐시에 바로 넣고, 경매 자체는 다시 부르지 않는다', () => {
    vi.useFakeTimers();
    const { client, invalidated } = setup();
    act(() => state.onMessage?.(bid(11000)));

    expect(client.getQueryData<{ currentPrice: number }>(auctionKey(7))?.currentPrice).toBe(11000);
    expect(invalidated()).not.toContain(JSON.stringify(auctionKey(7)));
  });

  it('입찰이 몰려도 입찰 기록·내 입찰 상태는 1초 동안 모아 한 번만 다시 부른다', () => {
    vi.useFakeTimers();
    const { invalidated } = setup();
    act(() => {
      state.onMessage?.(bid(11000));
      state.onMessage?.(bid(12000));
      state.onMessage?.(bid(13000));
    });
    expect(invalidated()).toHaveLength(0);

    act(() => vi.advanceTimersByTime(BID_REFRESH_DELAY_MS));
    // my-bid를 아직 모르는 상태라 포인트도 함께 다시 부른다
    expect(invalidated()).toEqual([
      JSON.stringify([...auctionKey(7), 'bids']),
      JSON.stringify([...auctionKey(7), 'my-bid']),
      JSON.stringify(['points', 'wallet']),
    ]);
  });

  it('입찰에 참여했으면 상태와 관계없이 1초 묶음에서 내 포인트를 다시 부른다 (my-bid 캐시가 늦어도 놓치지 않음)', () => {
    vi.useFakeTimers();
    for (const myBidState of ['LEADING', 'OUTBID', undefined]) {
      const { invalidated } = setup(myBidState);
      act(() => state.onMessage?.(bid(11000)));
      expect(invalidated()).not.toContain(JSON.stringify(['points', 'wallet'])); // 바로 부르지 않고
      act(() => vi.advanceTimersByTime(BID_REFRESH_DELAY_MS));
      expect(invalidated()).toContain(JSON.stringify(['points', 'wallet'])); // 1초 묶음에서 부른다
    }
  });

  it('입찰한 적 없는 게 확실하면(NONE) 내 포인트를 다시 부르지 않는다', () => {
    vi.useFakeTimers();
    const { invalidated } = setup('NONE');
    act(() => state.onMessage?.(bid(11000)));
    act(() => vi.advanceTimersByTime(BID_REFRESH_DELAY_MS));
    expect(invalidated()).not.toContain(JSON.stringify(['points', 'wallet']));
  });

  it('경매가 끝나면 경매를 다시 불러온다', () => {
    const { invalidated } = setup();
    act(() => state.onMessage?.({ auctionId: 7, result: 'ENDED', finalPrice: 13000, productName: '카메라' }));
    expect(invalidated()).toContain(JSON.stringify(auctionKey(7)));
  });
});
