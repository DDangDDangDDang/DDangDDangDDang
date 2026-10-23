import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BidDialog } from './BidDialog';

type Request = { path: string };

// 진행 중인 경매(현재가 15,000P, 단위 1,000P, 입찰 3회)와 내 지갑(잔액 20,000P). walletFails면 지갑 조회가 실패한다.
const state = vi.hoisted(() => ({ walletFails: false }));
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
      if (r.path === '/api/v1/points/wallet') {
        if (state.walletFails) throw new original.ApiError('COMMON_500', '서버 오류', 500);
        return { balance: 20000 };
      }
      return undefined;
    },
  };
});

function renderDialog(showDetailLink?: boolean) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <BidDialog auctionId={1} onClose={() => {}} showDetailLink={showDetailLink} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('BidDialog', () => {
  beforeEach(() => {
    state.walletFails = false;
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

  it('아래에 입찰 전 경고문(취소 불가·낙찰 시 되돌릴 수 없음)과 경매 상세 링크를 보여준다', async () => {
    renderDialog();
    const warning = await screen.findByRole('list', { name: '입찰 전 확인' });
    expect(warning).toHaveTextContent('입찰은 취소할 수 없어요.');
    expect(warning).toHaveTextContent('낙찰되면 묶인 포인트가 결제되고 되돌릴 수 없어요.');
    expect(screen.getByRole('link', { name: '경매 상세 새 탭에서 보기' })).toBeInTheDocument();
  });

  it('경매 상세에서 연 입찰 창에는 [경매 상세 새 탭에서 보기]가 없다', async () => {
    renderDialog(false);
    expect(await screen.findByRole('list', { name: '입찰 전 확인' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '경매 상세 새 탭에서 보기' })).not.toBeInTheDocument();
  });

  it('잔액보다 큰 금액은 입찰 단계로 넘어가지 않고 입력 아래에 알려준다', async () => {
    renderDialog();
    await screen.findByText('20,000 P');
    await userEvent.type(screen.getByLabelText('입찰 금액'), '30000');
    await userEvent.click(screen.getByRole('button', { name: '30,000 P 입찰하기' }));

    expect(screen.getByText('사용 가능 포인트가 부족해요. 포인트 화면에서 충전할 수 있어요.')).toBeInTheDocument();
    expect(screen.queryByText(/입찰 기능을 준비하고 있어요/)).not.toBeInTheDocument();
  });

  it('내 포인트를 불러오지 못하면 알려주고, [다시 불러오기]로 다시 불러온다', async () => {
    state.walletFails = true;
    renderDialog();
    expect(await screen.findByText(/내 포인트를 불러오지 못했어요/)).toBeInTheDocument();

    state.walletFails = false;
    await userEvent.click(screen.getByRole('button', { name: '다시 불러오기' }));
    expect(await screen.findByText('20,000 P')).toBeInTheDocument();
    expect(screen.queryByText(/내 포인트를 불러오지 못했어요/)).not.toBeInTheDocument();
  });

  it('최소 입찰가보다 낮으면 알려준다', async () => {
    renderDialog();
    await userEvent.type(await screen.findByLabelText('입찰 금액'), '15500');
    await userEvent.click(screen.getByRole('button', { name: '15,500 P 입찰하기' }));

    expect(screen.getByText('16,000 P부터 입찰할 수 있어요.')).toBeInTheDocument();
  });
});
