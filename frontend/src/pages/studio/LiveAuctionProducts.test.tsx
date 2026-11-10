import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LiveAuctionProducts } from './LiveAuctionPanel';
import type { LiveAuctions } from './use-live-auctions';

type Query = { status?: string; categoryId?: number; keyword?: string; page?: number; size?: number; sort?: string[] };
type Request = { path: string; query?: Query };
// 판매 중 상품 23개 = 10개씩 3페이지. '상품 1'~'상품 20'과 라이카 3개(렌즈·M6는 카메라(1) › 렌즈(11), 스트랩은 다른 카테고리).
// 받은 요청의 조건을 남겨 둔다.
const state = vi.hoisted(() => ({ queries: [] as Query[], categoriesFailed: false, refetchCategories: (() => undefined) as () => void }));
const PRODUCTS = [
  ...Array.from({ length: 20 }, (_, i) => ({ id: i + 1, name: `상품 ${i + 1}`, categoryId: 2 })),
  { id: 21, name: '라이카 렌즈', categoryId: 11 },
  { id: 22, name: '라이카 M6', categoryId: 11 },
  { id: 23, name: '라이카 스트랩', categoryId: 2 },
];

vi.mock('@/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api')>()),
  api: { GET: (path: string, init?: { params?: { query?: Query } }) => ({ path, query: init?.params?.query }) },
  // 서버처럼 카테고리(대분류면 그 아래 소분류까지)와 검색어(대소문자·앞뒤 공백 무시)를 함께 걸고 10개씩 나눈다.
  call: async (r: Request) => {
    if (r.path !== '/api/v1/users/me/products') return undefined;
    const query = r.query ?? {};
    state.queries.push(query);
    const page = query.page ?? 0;
    const keyword = query.keyword?.trim().toLowerCase();
    const matched = PRODUCTS.filter(
      (p) =>
        (!query.categoryId || p.categoryId === query.categoryId || (query.categoryId === 1 && p.categoryId === 11)) &&
        (!keyword || p.name.toLowerCase().includes(keyword)),
    );
    return {
      totalPages: Math.ceil(matched.length / 10),
      content: matched.slice(page * 10, page * 10 + 10).map((p, i) => ({
        id: p.id,
        name: p.name,
        status: 'ACTIVE',
        thumbnailUrl: i === 0 ? `https://cdn/thumb-${page}.png` : undefined,
      })),
    };
  },
}));
vi.mock('@/lib/use-categories', () => ({
  useCategories: () =>
    state.categoriesFailed
      ? { isError: true, data: undefined, refetch: state.refetchCategories }
      : { data: [{ id: 1, name: '카메라', children: [{ id: 11, name: '렌즈' }] }] },
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
  it('카테고리를 못 불러오면 이유와 다시 시도를 보여 주고, 목록은 전체로 계속 보인다', async () => {
    state.queries = [];
    state.categoriesFailed = true;
    state.refetchCategories = vi.fn();
    try {
      renderProducts();

      expect(await screen.findByRole('list', { name: '경매에 올릴 상품' })).toBeInTheDocument();
      expect(screen.getByRole('alert')).toHaveTextContent('카테고리를 불러오지 못했어요.');
      await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));
      expect(state.refetchCategories).toHaveBeenCalled();
    } finally {
      state.categoriesFailed = false;
    }
  });

  it('판매 중인 상품을 10개씩 페이지로 보여 주고, 대표 이미지를 붙이며, 카테고리로 거른다', async () => {
    state.queries = [];
    renderProducts();

    const list = await screen.findByRole('list', { name: '경매에 올릴 상품' });
    expect(list.querySelectorAll(':scope > li')).toHaveLength(10);
    // 등록 시각이 같아도 페이지가 겹치거나 빠지지 않게 id를 보조 정렬로 둔다.
    expect(state.queries[0]).toMatchObject({ status: 'ACTIVE', page: 0, size: 10, sort: ['createdAt,desc', 'id,desc'] });
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

/** 목록에 보이는 상품 이름들 */
function shownNames() {
  return within(screen.getByRole('list', { name: '경매에 올릴 상품' }))
    .getAllByRole('listitem')
    .map((item) => PRODUCTS.find((p) => within(item).queryByText(p.name))?.name);
}

/** 검색창을 비우고 text를 입력한다. (빈 문자열이면 비우기만) */
async function typeKeyword(text: string) {
  const input = screen.getByRole('searchbox', { name: '상품 이름 검색' });
  await userEvent.clear(input);
  if (text) await userEvent.type(input, text);
}

describe('스튜디오 경매 상품 이름 검색', () => {
  beforeEach(() => {
    state.queries = [];
  });

  it('이름 일부를 입력하면 입력이 멈춘 뒤 한 번만 검색해, 그 글자가 든 판매 중 상품만 보여 준다', async () => {
    renderProducts();
    await screen.findByText('상품 1');

    await typeKeyword('상품 2');

    await waitFor(() => expect(shownNames()).toEqual(['상품 2', '상품 20']));
    // 글자마다 요청하지 않고, 입력이 멈춘 뒤 마지막 검색어로 한 번만 요청한다.
    // 늦게 오는 요청도 잡도록 검색 대기 시간(0.3초)보다 조금 더 기다린 뒤 센다.
    await new Promise((resolve) => setTimeout(resolve, 400));
    expect(state.queries.filter((q) => q.keyword)).toEqual([expect.objectContaining({ status: 'ACTIVE', keyword: '상품 2', page: 0 })]);
    expect(screen.getByRole('searchbox', { name: '상품 이름 검색' })).toHaveAttribute('maxLength', '50');
  });

  it('카테고리를 고른 상태에서 검색하면 두 조건을 함께 건다', async () => {
    renderProducts();
    await screen.findByText('상품 1');
    await userEvent.selectOptions(screen.getByLabelText('카테고리로 거르기'), '1');
    await screen.findByText('라이카 렌즈');

    await typeKeyword('라이카');

    await waitFor(() => expect(state.queries.at(-1)).toMatchObject({ categoryId: 1, keyword: '라이카' }));
    // 다른 카테고리의 '라이카 스트랩'은 나오지 않는다.
    await waitFor(() => expect(shownNames()).toEqual(['라이카 렌즈', '라이카 M6']));
  });

  it('검색어를 지우면 다시 전체 목록을 보여 준다', async () => {
    renderProducts();
    await screen.findByText('상품 1');
    await typeKeyword('라이카');
    await waitFor(() => expect(shownNames()).toEqual(['라이카 렌즈', '라이카 M6', '라이카 스트랩']));

    await typeKeyword('');

    await waitFor(() => expect(shownNames()).toHaveLength(10));
    expect(state.queries.at(-1)?.keyword).toBeUndefined();
  });

  it('2페이지에서 검색하면 1페이지부터 보여 준다', async () => {
    renderProducts();
    await screen.findByText('상품 1');
    await userEvent.click(screen.getByRole('button', { name: '다음 페이지' }));
    await screen.findByText('상품 11');

    await typeKeyword('상품');

    await waitFor(() => expect(state.queries.at(-1)).toMatchObject({ keyword: '상품', page: 0 }));
    expect(await screen.findByText('상품 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
  });

  it('검색 결과가 없으면 검색어를 넣어 안내한다', async () => {
    renderProducts();
    await screen.findByText('상품 1');

    await typeKeyword('없는상품');

    expect(await screen.findByText('‘없는상품’이(가) 들어간 판매 중인 상품이 없어요.')).toBeInTheDocument();
  });
});
