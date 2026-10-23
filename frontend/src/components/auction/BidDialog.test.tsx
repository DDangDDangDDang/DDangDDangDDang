import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BidDialog } from './BidDialog';

type Request = { path: string; method?: string; init?: { body?: { amount?: number } } };

// 진행 중인 경매(현재가 15,000P, 단위 1,000P, 입찰 3회)와 내 지갑(잔액 20,000P). walletFails면 지갑 조회가 실패한다.
const state = vi.hoisted(() => ({ walletFails: false, bidFails: false, currentPrice: 15000, onAuctionMessage: undefined as ((message: unknown) => void) | undefined, buyNowPrice: undefined as number | undefined, posts: [] as { path: string; amount?: number }[] }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const endAt = new Date(Date.now() + 10 * 60_000).toISOString();
  return {
    ...original,
    useSession: () => ({ accessToken: 't', user: { userId: 2 } }),
    api: {
      GET: (path: string) => ({ path, method: 'GET' }),
      POST: (path: string, init: Request['init']) => ({ path, method: 'POST', init }),
    },
    // 실시간 연결은 하지 않고, 경매 메시지를 받는 함수만 남겨 테스트에서 직접 보낸다.
    connectRealtime: () => ({
      subscribe: (_destination: string, onMessage: (message: unknown) => void) => {
        state.onAuctionMessage = onMessage;
        return () => undefined;
      },
      close: () => undefined,
    }),
    call: async (r: Request) => {
      if (r.method === 'POST') {
        state.posts.push({ path: r.path, amount: r.init?.body?.amount });
        if (r.path.endsWith('/bids')) {
          if (state.bidFails) throw new original.ApiError('AUCTION_016', '입찰가가 최소 입찰가보다 낮습니다.', 400);
          return { bidId: 9, auctionId: 1, amount: r.init?.body?.amount, bidCount: 4, endAt, extended: true };
        }
        return { auctionId: 1, orderId: 77, price: state.buyNowPrice };
      }
      if (r.path === '/api/v1/auctions/{auctionId}')
        return { id: 1, productName: '필름 카메라', status: 'ONGOING', startPrice: 10000, currentPrice: state.currentPrice, bidUnit: 1000, bidCount: 3, endAt, buyNowPrice: state.buyNowPrice };
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
    state.bidFails = false;
    state.currentPrice = 15000;
    state.onAuctionMessage = undefined;
    state.buyNowPrice = undefined;
    state.posts = [];
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('[최소]를 누르면 현재가 + 입찰 단위가 들어가고, 한 번 더 확인한 뒤 입찰한다', async () => {
    renderDialog();
    await userEvent.click(await screen.findByRole('button', { name: '최소' }));
    expect(screen.getByLabelText('입찰 금액')).toHaveValue('16,000');

    await userEvent.click(screen.getByRole('button', { name: '16,000 P 입찰하기' }));
    // 확인 전에는 보내지 않는다
    expect(screen.getByRole('group', { name: '입찰 확인' })).toHaveTextContent('16,000 P로 입찰할까요?');
    expect(state.posts).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: '16,000 P 입찰 확정' }));
    expect(await screen.findByText(/16,000 P로 입찰했어요\. 지금 최고 입찰자예요\./)).toBeInTheDocument();
    expect(screen.getByText(/마감이 30초 늘어났어요/)).toBeInTheDocument();
    expect(state.posts).toEqual([{ path: '/api/v1/auctions/{auctionId}/bids', amount: 16000 }]);
  });

  it('입찰이 거절되면(그 사이 현재가가 오름) 이유를 보여 준다', async () => {
    state.bidFails = true;
    renderDialog();
    await userEvent.click(await screen.findByRole('button', { name: '최소' }));
    await userEvent.click(screen.getByRole('button', { name: '16,000 P 입찰하기' }));
    await userEvent.click(screen.getByRole('button', { name: '16,000 P 입찰 확정' }));
    expect(await screen.findByText('그 사이 현재가가 올랐어요. 금액을 다시 정해 주세요.')).toBeInTheDocument();
    expect(screen.queryByRole('group', { name: '입찰 확인' })).not.toBeInTheDocument();

    // 금액을 다시 정하면 지난 입찰의 오류는 지운다.
    await userEvent.click(screen.getByRole('button', { name: '+1,000' }));
    expect(screen.queryByText('그 사이 현재가가 올랐어요. 금액을 다시 정해 주세요.')).not.toBeInTheDocument();
  });

  it('입찰한 뒤 더 높은 입찰이 들어오면 최고 입찰자 안내 대신 밀렸다고 알린다', async () => {
    renderDialog();
    await userEvent.click(await screen.findByRole('button', { name: '최소' }));
    await userEvent.click(screen.getByRole('button', { name: '16,000 P 입찰하기' }));
    await userEvent.click(screen.getByRole('button', { name: '16,000 P 입찰 확정' }));
    expect(await screen.findByText(/지금 최고 입찰자예요/)).toBeInTheDocument();

    // 그 뒤 다른 사람이 17,000P로 입찰했다는 실시간 메시지가 온다.
    state.currentPrice = 17000;
    act(() => state.onAuctionMessage?.({ auctionId: 1, bidderId: 3, amount: 17000, bidCount: 5, endAt: new Date(Date.now() + 10 * 60_000).toISOString(), extended: false }));
    expect(await screen.findByText('16,000 P 입찰보다 높은 입찰이 들어왔어요. 다시 입찰할 수 있어요.')).toBeInTheDocument();
    expect(screen.queryByText(/지금 최고 입찰자예요/)).not.toBeInTheDocument();
  });

  it('즉시구매가가 있으면 한 번 더 확인한 뒤 즉시 구매하고, 주문 내역으로 안내한다', async () => {
    state.buyNowPrice = 19000;
    renderDialog();
    await userEvent.click(await screen.findByRole('button', { name: /즉시 구매 19,000 P/ }));
    expect(screen.getByRole('group', { name: '즉시 구매 확인' })).toHaveTextContent('19,000 P에 바로 낙찰받을까요?');
    await userEvent.click(screen.getByRole('button', { name: '19,000 P 즉시 구매 확정' }));
    expect(state.posts).toEqual([{ path: '/api/v1/auctions/{auctionId}/buy-now', amount: undefined }]);
    // 경매가 아직 진행 중으로 보여도 결과를 보여 주고, 더 입찰·구매하지 못하게 한다.
    expect(await screen.findByText(/19,000 P에 즉시 구매했어요/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /입찰하기/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /즉시 구매/ })).not.toBeInTheDocument();
  });

  it('즉시구매가 이상으로는 입찰하지 않고 즉시 구매로 안내한다', async () => {
    state.buyNowPrice = 19000;
    renderDialog();
    await userEvent.type(await screen.findByLabelText('입찰 금액'), '19000');
    await userEvent.click(screen.getByRole('button', { name: '19,000 P 입찰하기' }));
    expect(screen.getByText('즉시구매가 이상으로는 입찰할 수 없어요. [즉시 구매]를 이용해 주세요.')).toBeInTheDocument();
    expect(state.posts).toEqual([]);
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
