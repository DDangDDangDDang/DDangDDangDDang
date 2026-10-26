import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuctionDetailPage } from './AuctionDetailPage';

type Request = { path: string };
const SELLER_ID = 7;
// 지금 보는 경매(유찰)와 예전 경매(취소). history가 비면 빈 배열을 돌려준다.
const state = vi.hoisted(() => ({ userId: 7 as number | undefined, history: [] as unknown[], paths: [] as string[] }));

vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => (state.userId === undefined ? null : { accessToken: 't', user: { userId: state.userId } }),
    api: { GET: (path: string) => ({ path }) },
    call: async (r: Request) => {
      state.paths.push(r.path);
      if (r.path === '/api/v1/auctions/{auctionId}')
        return {
          id: 20,
          productId: 5,
          sellerId: SELLER_ID,
          productName: '필름 카메라',
          status: 'FAILED',
          startPrice: 12_000,
          currentPrice: 12_000,
          bidCount: 0,
          startAt: '2026-10-20T10:00:00',
          endAt: '2026-10-21T10:00:00',
        };
      if (r.path === '/api/v1/products/{productId}') return { id: 5, description: '잘 작동해요.' };
      if (r.path === '/api/v1/products/{productId}/auctions') return state.history;
      return undefined;
    },
  };
});
// 실시간 연결·알림 버튼·판매자 이름은 이 테스트와 상관없어 단순하게 바꾼다.
vi.mock('@/lib/use-auction-realtime', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/use-auction-realtime')>()),
  useAuctionRealtime: () => undefined,
}));
vi.mock('@/components/auction/AuctionAlertButton', () => ({ AuctionAlertButton: () => null }));
vi.mock('@/components/seller/SellerIdentity', () => ({ SellerIdentity: () => <span>판매자</span> }));

function renderDetail() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/auctions/20']}>
        <Routes>
          <Route path="/auctions/:auctionId" element={<AuctionDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('경매 상세 - 이 상품의 경매 이력', () => {
  beforeEach(() => {
    state.userId = SELLER_ID;
    state.paths = [];
    state.history = [
      { id: 20, status: 'FAILED', startPrice: 12_000, currentPrice: 12_000, bidCount: 0, startAt: '2026-10-20T10:00:00', endAt: '2026-10-21T10:00:00' },
      { id: 11, status: 'CANCELED', startPrice: 15_000, currentPrice: 15_000, bidCount: 0, startAt: '2026-10-10T10:00:00', endAt: '2026-10-11T10:00:00' },
    ];
  });

  it('판매자 본인이면 상품 설명 아래에 이 상품으로 열었던 경매를 보여 주고, 예전 경매는 눌러서 그 경매로 간다', async () => {
    renderDetail();
    const history = await screen.findByRole('list', { name: '이 상품의 경매 이력' });
    const rows = within(history).getAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent('유찰');
    expect(rows[0]).toHaveTextContent('지금 보는 경매');
    expect(rows[0]).toHaveTextContent('12,000 P');
    expect(rows[1]).toHaveTextContent('취소됨');
    expect(rows[1]).toHaveTextContent('15,000 P');
    expect(within(rows[1] as HTMLElement).getByRole('link')).toHaveAttribute('href', '/auctions/11');
    // 지금 보는 경매는 링크가 아니다.
    expect(within(rows[0] as HTMLElement).queryByRole('link')).toBeNull();
  });

  it('가격이 응답에 없으면 0 P가 아니라 -로 보여 준다', async () => {
    state.history = [{ id: 20, status: 'FAILED', bidCount: 0, startAt: '2026-10-20T10:00:00', endAt: '2026-10-21T10:00:00' }];
    renderDetail();
    const row = within(await screen.findByRole('list', { name: '이 상품의 경매 이력' })).getByRole('listitem');
    expect(within(row).getAllByText('-')).toHaveLength(2);
    expect(row).not.toHaveTextContent('0 P');
  });

  it('이력이 비었으면 "경매 이력이 없어요"', async () => {
    state.history = [];
    renderDetail();
    expect(await screen.findByText('경매 이력이 없어요')).toBeInTheDocument();
  });

  it('판매자가 아니면 영역을 그리지 않고 이력 API도 부르지 않는다', async () => {
    state.userId = 99;
    renderDetail();
    expect(await screen.findByText('잘 작동해요.')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '이 상품의 경매 이력' })).toBeNull();
    expect(state.paths).not.toContain('/api/v1/products/{productId}/auctions');
  });
});
