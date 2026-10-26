import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SellerProfileDialog } from './SellerProfileDialog';

type AuctionQuery = { tab?: string; includeFailedAndCanceled?: boolean; cursor?: number; size?: number };
type Request = { path: string; init?: { params?: { query?: AuctionQuery } } };

// 탭별 경매. 지난 경매는 유찰·취소 포함 여부에 따라 다르게 돌려준다.
const AUCTIONS: Record<string, { auctionId: number; productName: string; status: string; startPrice: number; currentPrice: number }[]> = {
  ONGOING: [{ auctionId: 1, productName: '진행 중 카메라', status: 'ONGOING', startPrice: 1_000, currentPrice: 5_000 }],
  READY: [{ auctionId: 2, productName: '시작 전 시계', status: 'READY', startPrice: 3_000, currentPrice: 3_000 }],
  PAST: [{ auctionId: 3, productName: '낙찰 렌즈', status: 'ENDED', startPrice: 1_000, currentPrice: 9_000 }],
  PAST_ALL: [
    { auctionId: 3, productName: '낙찰 렌즈', status: 'ENDED', startPrice: 1_000, currentPrice: 9_000 },
    { auctionId: 4, productName: '유찰 가방', status: 'FAILED', startPrice: 7_000, currentPrice: 7_000 },
    { auctionId: 5, productName: '취소 신발', status: 'CANCELED', startPrice: 2_000, currentPrice: 2_000 },
  ],
};
const state = vi.hoisted(() => ({ auctionQueries: [] as AuctionQuery[] }));

vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => null,
    api: { GET: (path: string, init: Request['init']) => ({ path, init }) },
    call: async (r: Request) => {
      if (r.path === '/api/v1/sellers/{sellerId}/profile') return { sellerId: 7, nickname: '필름상점', subscriberCount: 3, trust: {} };
      if (r.path === '/api/v1/sellers/{sellerId}/auctions') {
        const query = r.init?.params?.query ?? {};
        state.auctionQueries.push(query);
        const key = query.tab === 'PAST' && query.includeFailedAndCanceled ? 'PAST_ALL' : (query.tab ?? 'ONGOING');
        return { content: AUCTIONS[key], hasNext: false };
      }
      return undefined;
    },
  };
});

async function openAuctionsTab() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <SellerProfileDialog sellerId={7} onClose={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await userEvent.click(await screen.findByRole('tab', { name: '경매 기록' }));
}

const list = () => screen.findByRole('list', { name: '경매 기록' });

describe('판매자 프로필 경매 기록 탭', () => {
  beforeEach(() => {
    state.auctionQueries = [];
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('기본은 진행 중 탭이고, 탭을 바꾸면 그 상태의 경매만 처음부터 다시 불러온다', async () => {
    await openAuctionsTab();
    expect(screen.getByRole('tab', { name: '진행 중' })).toHaveAttribute('aria-selected', 'true');
    expect(within(await list()).getByText('진행 중 카메라')).toBeInTheDocument();
    expect(state.auctionQueries[0]).toEqual({ tab: 'ONGOING', cursor: undefined, size: 10 });
    // 진행 중·시작 전 탭에는 유찰·취소 체크가 없다.
    expect(screen.queryByRole('checkbox', { name: '유찰·취소 포함' })).toBeNull();

    await userEvent.click(screen.getByRole('tab', { name: '시작 전' }));
    expect(within(await list()).getByText('시작 전 시계')).toBeInTheDocument();
    expect(screen.queryByText('진행 중 카메라')).toBeNull();
    expect(state.auctionQueries.at(-1)).toEqual({ tab: 'READY', cursor: undefined, size: 10 });
  });

  it('지난 경매는 기본으로 낙찰만, "유찰·취소 포함"을 켜면 유찰·취소도 커서를 처음부터 다시 불러온다', async () => {
    await openAuctionsTab();
    await userEvent.click(screen.getByRole('tab', { name: '지난 경매' }));
    const pastList = await list();
    expect(within(pastList).getByText('낙찰 렌즈')).toBeInTheDocument();
    expect(within(pastList).queryByText('유찰 가방')).toBeNull();
    expect(state.auctionQueries.at(-1)).toEqual({ tab: 'PAST', includeFailedAndCanceled: false, cursor: undefined, size: 10 });

    const checkbox = screen.getByRole('checkbox', { name: '유찰·취소 포함' });
    expect(checkbox).not.toBeChecked();
    await userEvent.click(checkbox);
    expect(within(await screen.findByRole('list', { name: '경매 기록' })).getByText('유찰 가방')).toBeInTheDocument();
    expect(screen.getByText('취소 신발')).toBeInTheDocument();
    expect(state.auctionQueries.at(-1)).toEqual({ tab: 'PAST', includeFailedAndCanceled: true, cursor: undefined, size: 10 });
    // 유찰·취소는 시작가, 낙찰은 낙찰가로 보여 준다.
    expect(screen.getAllByText('시작가')).toHaveLength(2);
    expect(screen.getByText('낙찰가')).toBeInTheDocument();
  });
});
