import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BidDialog } from './BidDialog';

type Request = { path: string };

// 진행 중인 경매(현재가 15,000P, 단위 1,000P, 입찰 3회)와 내 지갑(잔액 20,000P)
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const endAt = new Date(Date.now() + 10 * 60_000).toISOString();
  return {
    ...original,
    useSession: () => ({ accessToken: 't', user: { userId: 2 } }),
    api: { GET: (path: string) => ({ path }) },
    call: async (r: Request) => {
      if (r.path === '/api/v1/auctions/{auctionId}')
        return { id: 1, productName: '필름 카메라', status: 'ONGOING', startPrice: 10000, currentPrice: 15000, bidUnit: 1000, bidCount: 3, endAt };
      if (r.path === '/api/v1/points/wallet') return { balance: 20000 };
      return undefined;
    },
  };
});

function renderDialog() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <BidDialog auctionId={1} onClose={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BidDialog', () => {
  beforeEach(() => {
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('[최소]를 누르면 현재가 + 입찰 단위가 들어가고, 입찰하면 준비 중 안내를 보여준다', async () => {
    renderDialog();
    await userEvent.click(await screen.findByRole('button', { name: '최소' }));
    expect(screen.getByLabelText('입찰 금액')).toHaveValue('16,000');

    await userEvent.click(screen.getByRole('button', { name: '16,000 P 입찰하기' }));
    expect(await screen.findByText(/입찰 기능을 준비하고 있어요/)).toBeInTheDocument();
  });

  it('잔액보다 큰 금액은 입찰 단계로 넘어가지 않고 입력 아래에 알려준다', async () => {
    renderDialog();
    await screen.findByText('20,000 P');
    await userEvent.type(screen.getByLabelText('입찰 금액'), '30000');
    await userEvent.click(screen.getByRole('button', { name: '30,000 P 입찰하기' }));

    expect(screen.getByText('사용 가능 포인트가 부족해요. 포인트 화면에서 충전할 수 있어요.')).toBeInTheDocument();
    expect(screen.queryByText(/입찰 기능을 준비하고 있어요/)).not.toBeInTheDocument();
  });

  it('최소 입찰가보다 낮으면 알려준다', async () => {
    renderDialog();
    await userEvent.type(await screen.findByLabelText('입찰 금액'), '15500');
    await userEvent.click(screen.getByRole('button', { name: '15,500 P 입찰하기' }));

    expect(screen.getByText('16,000 P부터 입찰할 수 있어요.')).toBeInTheDocument();
  });
});
