import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { LiveAuctionProducts } from './LiveAuctionPanel';
import type { LiveAuctions } from './use-live-auctions';

type Query = { status?: string; categoryId?: number; page?: number; size?: number };
type Request = { path: string; query?: Query };
// 판매 중 상품 23개 = 10개씩 3페이지. 받은 요청의 조건을 남겨 둔다.
const state = vi.hoisted(() => ({ queries: [] as Query[] }));

vi.mock('@/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api')>()),
  api: { GET: (path: string, init?: { params?: { query?: Query } }) => ({ path, query: init?.params?.query }) },
  call: async (r: Request) => {
    if (r.path !== '/api/v1/users/me/products') return undefined;
    const query = r.query ?? {};
    state.queries.push(query);
    const page = query.page ?? 0;
    const total = query.categoryId ? 1 : 23;
    const count = Math.max(0, Math.min(10, total - page * 10));
    return {
      totalPages: Math.ceil(total / 10),
      content: Array.from({ length: count }, (_, i) => ({
        id: page * 10 + i + 1,
        name: query.categoryId ? '라이카 렌즈' : `상품 ${page * 10 + i + 1}`,
        status: 'ACTIVE',
        thumbnailUrl: i === 0 ? `https://cdn/thumb-${page}.png` : undefined,
      })),
    };
  },
}));
vi.mock('@/lib/use-categories', () => ({
  useCategories: () => ({ data: [{ id: 1, name: '카메라', children: [{ id: 11, name: '렌즈' }] }] }),
}));

function renderProducts() {
  const auctions = { liveStreamId: 1, activeAuctionOf: () => undefined, start: { isPending: false, isError: false } } as unknown as LiveAuctions;
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <LiveAuctionProducts live auctions={auctions} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('스튜디오 경매 상품 목록', () => {
  it('판매 중인 상품을 10개씩 페이지로 보여 주고, 대표 이미지를 붙이며, 카테고리로 거른다', async () => {
    state.queries = [];
    renderProducts();

    const list = await screen.findByRole('list', { name: '경매에 올릴 상품' });
    expect(list.querySelectorAll(':scope > li')).toHaveLength(10);
    expect(state.queries[0]).toMatchObject({ status: 'ACTIVE', page: 0, size: 10 });
    expect(screen.getByRole('img', { name: '상품 1' })).toHaveAttribute('src', 'https://cdn/thumb-0.png');

    // 다음 페이지
    await userEvent.click(screen.getByRole('button', { name: '다음 페이지' }));
    expect(await screen.findByText('상품 11')).toBeInTheDocument();
    expect(state.queries.at(-1)).toMatchObject({ page: 1 });

    // 카테고리를 고르면 첫 페이지부터 그 카테고리 상품만
    await userEvent.selectOptions(screen.getByLabelText('카테고리로 거르기'), '1');
    expect(await screen.findByText('라이카 렌즈')).toBeInTheDocument();
    expect(state.queries.at(-1)).toMatchObject({ status: 'ACTIVE', categoryId: 1, page: 0 });
    expect(screen.queryByRole('navigation', { name: '경매 상품 페이지' })).not.toBeInTheDocument();
  });
});
