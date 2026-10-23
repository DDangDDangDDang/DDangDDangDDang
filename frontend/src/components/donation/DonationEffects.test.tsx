import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChatMessage } from '@/api';
import { DonationEffects } from './DonationEffects';
import { DonationSoundToggle } from './DonationSoundToggle';

const chime = vi.hoisted(() => vi.fn());
vi.mock('@/lib/donation-sound', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/donation-sound')>()),
  playDonationChime: chime,
}));

const gift = (id: string, amount: number): ChatMessage => ({
  messageId: id,
  type: 'DONATION',
  liveStreamId: 1,
  senderId: 3,
  nickname: '필름팬',
  content: null,
  sentAt: '2026-10-24T12:00:00',
  amount,
});

describe('DonationEffects 효과음', () => {
  beforeEach(() => {
    chime.mockClear();
    localStorage.clear();
  });

  it('효과음을 켜 두면 새 선물이 뜰 때 한 번 울리고, 꺼 두면 소리 없이 이펙트만 보여 준다', () => {
    const { rerender } = render(<DonationEffects messages={[]} sound />);
    rerender(<DonationEffects messages={[gift('a', 1_000)]} sound />);
    expect(screen.getByRole('status')).toHaveTextContent('필름팬님이 1,000 P를 선물했어요');
    expect(chime).toHaveBeenCalledTimes(1);
    expect(chime).toHaveBeenCalledWith(1_000);

    // 같은 이펙트를 보여 주는 동안 소리 설정을 바꿔도 다시 울리지 않는다.
    rerender(<DonationEffects messages={[gift('a', 1_000)]} sound={false} />);
    rerender(<DonationEffects messages={[gift('a', 1_000)]} sound />);
    expect(chime).toHaveBeenCalledTimes(1);
  });

  it('소리를 켜지 않은 시청자에게는 울리지 않는다', () => {
    const { rerender } = render(<DonationEffects messages={[]} />);
    rerender(<DonationEffects messages={[gift('b', 300)]} />);
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(chime).not.toHaveBeenCalled();
  });

  it('선물이 클수록 별이 많이 튀어나온다 (1,000P 미만 6개, 1,000P 이상 10개, 3,000P 이상 18개와 강조 테두리)', () => {
    const stars = (amount: number) => {
      const { container, rerender, unmount } = render(<DonationEffects messages={[]} />);
      rerender(<DonationEffects messages={[gift(String(amount), amount)]} />);
      const status = screen.getByRole('status');
      const count = container.querySelectorAll('[aria-hidden="true"].absolute > svg').length;
      const ring = status.className.includes('ring-2');
      unmount();
      return { count, ring };
    };
    expect(stars(500)).toEqual({ count: 6, ring: false });
    expect(stars(1_000)).toEqual({ count: 10, ring: false });
    expect(stars(3_000)).toEqual({ count: 18, ring: true });
  });

  it('스튜디오 효과음 켜기/끄기는 기본 켜짐이고, 끄면 기억하며, 켤 때 한 번 들려준다', async () => {
    const { unmount } = render(<DonationSoundToggle />);
    await userEvent.click(screen.getByRole('button', { name: '선물 효과음 켜짐' }));
    expect(screen.getByRole('button', { name: '선물 효과음 꺼짐' })).toHaveAttribute('aria-pressed', 'false');
    expect(chime).not.toHaveBeenCalled();
    unmount();

    render(<DonationSoundToggle />);
    await userEvent.click(screen.getByRole('button', { name: '선물 효과음 꺼짐' }));
    expect(screen.getByRole('button', { name: '선물 효과음 켜짐' })).toBeInTheDocument();
    expect(chime).toHaveBeenCalledTimes(1);
  });
});
