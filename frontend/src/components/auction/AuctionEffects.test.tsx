import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChatMessage } from '@/api';
import { AuctionEffects } from './AuctionEffects';

const sound = vi.hoisted(() => ({ coins: vi.fn(), gavel: vi.fn() }));
vi.mock('@/lib/auction-sound', () => ({ playBidCoins: sound.coins, playGavel: sound.gavel }));

const bid = (id: string, amount: number, previousAmount: number): ChatMessage => ({
  messageId: id,
  type: 'BID',
  liveStreamId: 1,
  senderId: 3,
  nickname: '필름팬',
  content: null,
  sentAt: '2026-10-24T12:00:00',
  amount,
  previousAmount,
  auctionId: 1,
  productName: '라이카 M6',
});

const sold = (id: string, amount: number): ChatMessage => ({ ...bid(id, amount, 0), type: 'AUCTION_SOLD', previousAmount: null });

describe('AuctionEffects', () => {
  beforeEach(() => {
    sound.coins.mockClear();
    sound.gavel.mockClear();
  });

  it('입찰이 오면 누가 어떤 상품을 얼마에 입찰했는지 보여 주고, 오른 비율에 맞는 동전 소리를 낸다', () => {
    const { rerender } = render(<AuctionEffects messages={[]} sound />);
    rerender(<AuctionEffects messages={[bid('a', 13_000, 10_000)]} sound />);

    const notice = screen.getByRole('status');
    expect(notice).toHaveTextContent('필름팬님이 ‘라이카 M6’ 상품을 13,000 P에 입찰했습니다.');
    expect(notice).toHaveTextContent('30% 올림');
    expect(notice).toHaveAttribute('data-tier', 'up30');
    expect(sound.coins).toHaveBeenCalledWith('up30');
  });

  it('조금 올린 입찰은 오른 비율을 붙이지 않고 동전도 떨어뜨리지 않는다', () => {
    const { container, rerender } = render(<AuctionEffects messages={[]} />);
    rerender(<AuctionEffects messages={[bid('a', 10_500, 10_000)]} />);

    expect(screen.getByRole('status')).not.toHaveTextContent('올림');
    expect(container.querySelectorAll('[data-coin]')).toHaveLength(0);
    expect(sound.coins).not.toHaveBeenCalled();
  });

  it('50% 이상 올린 입찰은 동전이 30% 단계보다 많이 떨어진다', () => {
    const coinsFor = (amount: number) => {
      const { container, rerender, unmount } = render(<AuctionEffects messages={[]} />);
      rerender(<AuctionEffects messages={[bid(String(amount), amount, 10_000)]} />);
      const count = container.querySelectorAll('[data-coin]').length;
      unmount();
      return count;
    };
    expect(coinsFor(15_000)).toBeGreaterThan(coinsFor(13_000));
    expect(coinsFor(13_000)).toBeGreaterThan(coinsFor(11_000));
  });

  it('낙찰되면 망치 애니메이션과 함께 어떤 상품이 누구에게 얼마에 낙찰됐는지 보여 주고 망치 소리를 낸다', () => {
    const { container, rerender } = render(<AuctionEffects messages={[]} sound />);
    rerender(<AuctionEffects messages={[sold('s', 21_000)]} sound />);

    expect(screen.getByRole('status')).toHaveTextContent('‘라이카 M6’ 상품이 필름팬님에게 21,000 P에 낙찰되었습니다.');
    expect(container.querySelector('[data-figure="gavel"]')).not.toBeNull();
    expect(sound.gavel).toHaveBeenCalledTimes(1);
  });
});
