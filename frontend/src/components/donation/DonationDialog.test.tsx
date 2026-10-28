import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DonationDialog } from './DonationDialog';

type Request = { path: string; method: 'GET' | 'POST'; init?: { body?: { amount?: number; message?: string; requestKey?: string } } };

// 선물할 수 있는 마일리지 2,000M, 이 판매자에게 이번 달 남은 한도 1,000M
const state = vi.hoisted(() => ({ giftFails: false, posts: [] as { amount?: number; message?: string; requestKey?: string }[] }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: {
      GET: (path: string) => ({ path, method: 'GET' }),
      POST: (path: string, init: Request['init']) => ({ path, method: 'POST', init }),
    },
    call: async (r: Request) => {
      if (r.method === 'POST') {
        state.posts.push({ ...r.init?.body });
        if (state.giftFails) throw new original.ApiError('DONATION_003', '이 판매자에게 이번 달 선물할 수 있는 마일리지는 0M 남았습니다.', 400);
        return { donationId: 1, amount: r.init?.body?.amount, giftableBalance: 1_500, remainingThisMonth: 500 };
      }
      return {
        sellerId: 2,
        giftableBalance: 2_000,
        monthlyLimit: 5_000,
        givenThisMonth: 4_000,
        remainingThisMonth: 1_000,
        minGift: 100,
        giftUnit: 100,
        maxGift: 5_000,
      };
    },
  };
});

function renderDialog() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <DonationDialog liveStreamId={7} sellerName="필름상점" open onClose={() => undefined} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('DonationDialog', () => {
  beforeEach(() => {
    state.giftFails = false;
    state.posts = [];
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('금액 버튼으로 고르고 한마디와 함께 선물하면, 선물했다고 알려 주고 다음 선물에는 새 요청 키를 쓴다', async () => {
    renderDialog();
    expect(await screen.findByText('2,000 M')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '500' }));
    await userEvent.type(screen.getByLabelText('함께 보낼 한마디 (선택)'), '오늘 방송 최고예요');
    await userEvent.click(screen.getByRole('button', { name: '500 M 선물하기' }));

    expect(await screen.findByText('500 M을 선물했어요. 방송 화면에 선물 알림이 떠요.')).toBeInTheDocument();
    expect(state.posts[0]).toMatchObject({ amount: 500, message: '오늘 방송 최고예요' });

    await userEvent.click(screen.getByRole('button', { name: '100' }));
    await userEvent.click(screen.getByRole('button', { name: '100 M 선물하기' }));
    await screen.findByText('100 M을 선물했어요. 방송 화면에 선물 알림이 떠요.');
    expect(state.posts[1]?.requestKey).not.toBe(state.posts[0]?.requestKey);
  });

  it('이번 달 남은 한도를 넘으면 보내지 않고 입력 아래에 알려 준다', async () => {
    renderDialog();
    await userEvent.click(await screen.findByRole('button', { name: '3,000' }));
    await userEvent.click(screen.getByRole('button', { name: '3,000 M 선물하기' }));
    expect(screen.getByText('이 판매자에게는 이번 달 1,000 M까지 더 선물할 수 있어요.')).toBeInTheDocument();
    expect(state.posts).toEqual([]);
  });

  it('서버가 거절하면 이유를 보여 준다', async () => {
    state.giftFails = true;
    renderDialog();
    await userEvent.click(await screen.findByRole('button', { name: '100' }));
    await userEvent.click(screen.getByRole('button', { name: '100 M 선물하기' }));
    expect(await screen.findByText('이 판매자에게 이번 달 선물할 수 있는 마일리지는 0M 남았습니다.')).toBeInTheDocument();
  });
});
