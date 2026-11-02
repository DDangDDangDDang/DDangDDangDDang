import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SellerProfileDialog } from './SellerProfileDialog';

type Reply = { content: string; repliedAt?: string };
type Review = { reviewId: number; sellerId: number; productName: string; rating: number; content?: string | null; createdAt: string; reply?: Reply | null };
type Request = { method: string; path: string; init?: { params?: { path?: { reviewId?: number }; query?: { cursor?: number; size?: number } }; body?: { content?: string } } };

const state = vi.hoisted(() => ({
  userId: undefined as number | undefined,
  trust: {} as Record<string, unknown>,
  reviews: [] as Review[],
  pageSize: 10,
  requests: [] as Request[],
}));

vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (method: string) => (path: string, init: Request['init']) => ({ method, path, init });
  return {
    ...original,
    useSession: () => (state.userId === undefined ? null : { accessToken: 't', user: { userId: state.userId } }),
    api: { GET: request('GET'), PUT: request('PUT'), DELETE: request('DELETE') },
    call: async (r: Request) => {
      state.requests.push(r);
      if (r.path === '/api/v1/sellers/{sellerId}/profile') return { sellerId: 7, nickname: '필름상점', subscriberCount: 3, trust: state.trust };
      if (r.path === '/api/v1/reviews') {
        // 커서는 마지막으로 받은 reviewId. 서버처럼 최신(큰 id)부터 준다.
        const cursor = r.init?.params?.query?.cursor;
        const rest = state.reviews.filter((review) => cursor === undefined || review.reviewId < cursor);
        const content = rest.slice(0, state.pageSize);
        const hasNext = rest.length > state.pageSize;
        return { content, hasNext, nextCursor: hasNext ? content.at(-1)?.reviewId : undefined };
      }
      const reviewId = r.init?.params?.path?.reviewId;
      const target = state.reviews.find((review) => review.reviewId === reviewId);
      if (r.path === '/api/v1/reviews/{reviewId}/reply' && target) {
        target.reply = r.method === 'PUT' ? { content: r.init?.body?.content ?? '', repliedAt: '2026-10-29T10:00:00' } : null;
        return r.method === 'PUT' ? target : undefined;
      }
      return undefined;
    },
  };
});

// 목록 끝(sentinel)이 보였다고 알려 줄 수 있는 IntersectionObserver
let intersect: (() => void) | undefined;
class FakeIntersectionObserver {
  constructor(callback: IntersectionObserverCallback) {
    intersect = () => callback([{ isIntersecting: true } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
  }
  observe() {}
  disconnect() {}
}

function review(reviewId: number, extra: Partial<Review> = {}): Review {
  return { reviewId, sellerId: 7, productName: `상품 ${reviewId}`, rating: 4, content: `리뷰 내용 ${reviewId}`, createdAt: '2026-10-20T12:00:00', ...extra };
}

function renderDialog() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <SellerProfileDialog sellerId={7} onClose={() => {}} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

async function openReviewsTab() {
  renderDialog();
  await userEvent.click(await screen.findByRole('tab', { name: '리뷰' }));
}

const list = () => screen.findByRole('list', { name: '리뷰' });
const itemOf = (text: string) => screen.getByText(text).closest('li') as HTMLElement;

describe('판매자 프로필 리뷰', () => {
  beforeEach(() => {
    state.userId = undefined;
    state.trust = { averageRating: 4.3, reviewCount: 3 };
    state.reviews = [];
    state.pageSize = 10;
    state.requests = [];
    intersect = undefined;
    vi.stubGlobal('IntersectionObserver', FakeIntersectionObserver);
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });
  afterEach(() => vi.unstubAllGlobals());

  it('탭은 기본 정보·경매 기록·리뷰 세 개다', async () => {
    renderDialog();
    const tabs = await screen.findAllByRole('tab');
    expect(tabs.slice(0, 3).map((tab) => tab.textContent)).toEqual(['기본 정보', '경매 기록', '리뷰']);
  });

  it('기본 정보의 구매자 만족도에 평균 별점과 리뷰 수를 보여 준다', async () => {
    renderDialog();
    const stat = (await screen.findByText('구매자 만족도')).closest('div') as HTMLElement;
    expect(within(stat).getByText('★ 4.3 / 리뷰 3개')).toBeInTheDocument();
    // 구독자 칸은 구매자 만족도로 바뀌었다. (구독자 수는 머리말에)
    expect(screen.queryByText('구독자', { selector: 'dt' })).toBeNull();
  });

  it('받은 리뷰가 없으면(평균 null) 구매자 만족도는 "아직 없음"', async () => {
    state.trust = { averageRating: null, reviewCount: 0 };
    renderDialog();
    const stat = (await screen.findByText('구매자 만족도')).closest('div') as HTMLElement;
    expect(within(stat).getByText('아직 없음')).toBeInTheDocument();
  });

  it('리뷰를 서버가 준 최신순으로, 맨 위에 평균·개수 요약과 함께 보여 준다', async () => {
    state.reviews = [review(3, { rating: 5 }), review(2), review(1, { rating: 3 })];
    await openReviewsTab();
    const items = within(await list()).getAllByRole('listitem');
    expect(items.map((item) => within(item).getByText(/^상품 \d$/).textContent)).toEqual(['상품 3', '상품 2', '상품 1']);
    expect(within(items[0] as HTMLElement).getByRole('img', { name: '별점 5점 만점에 5점' })).toBeInTheDocument();
    expect(within(items[0] as HTMLElement).getByText('리뷰 내용 3')).toBeInTheDocument();
    expect(screen.getByText('★ 4.3 · 리뷰 3개')).toBeInTheDocument();
    expect(state.requests.find((r) => r.path === '/api/v1/reviews')?.init?.params?.query).toEqual({ sellerId: 7, cursor: undefined, size: 10 });
  });

  it('내용 없이 별점만 남긴 리뷰는 별점·상품명만 보인다', async () => {
    state.reviews = [review(1, { content: null })];
    await openReviewsTab();
    const item = within(await list()).getByRole('listitem');
    expect(within(item).getByRole('img', { name: '별점 5점 만점에 4점' })).toBeInTheDocument();
    expect(within(item).getByText('상품 1')).toBeInTheDocument();
    expect(item.querySelector('p')).toBeNull();
  });

  it('판매자 답글이 있으면 리뷰 아래에 보여 준다', async () => {
    state.reviews = [review(1, { reply: { content: '감사합니다!' } })];
    await openReviewsTab();
    const item = within(await list()).getByRole('listitem');
    expect(within(item).getByText('판매자 답글')).toBeInTheDocument();
    expect(within(item).getByText('감사합니다!')).toBeInTheDocument();
  });

  it('리뷰가 없으면 안내 문구를 보여 준다', async () => {
    state.trust = { averageRating: null, reviewCount: 0 };
    await openReviewsTab();
    expect(await screen.findByText('아직 리뷰가 없어요')).toBeInTheDocument();
    expect(screen.queryByText(/★/)).toBeNull();
  });

  it('목록 끝까지 내리면 커서로 다음 페이지를 불러와 이어 붙인다', async () => {
    state.pageSize = 2;
    state.reviews = [review(5), review(4), review(3)];
    await openReviewsTab();
    expect(within(await list()).getAllByRole('listitem')).toHaveLength(2);
    intersect?.();
    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(3));
    expect(screen.getByText('상품 3')).toBeInTheDocument();
    const queries = state.requests.filter((r) => r.path === '/api/v1/reviews').map((r) => r.init?.params?.query?.cursor);
    expect(queries).toEqual([undefined, 4]);
  });

  it('남의 상점에서는 답글 버튼이 없다', async () => {
    state.userId = 99;
    state.reviews = [review(2), review(1, { reply: { content: '감사합니다!' } })];
    await openReviewsTab();
    await list();
    expect(screen.queryByRole('button', { name: '답글 달기' })).toBeNull();
    expect(screen.queryByRole('button', { name: '답글 수정' })).toBeNull();
    expect(screen.queryByRole('button', { name: '답글 삭제' })).toBeNull();
  });

  it('판매자 본인은 답글이 없으면 [답글 달기], 있으면 [답글 수정][답글 삭제]', async () => {
    state.userId = 7;
    state.reviews = [review(2), review(1, { reply: { content: '감사합니다!' } })];
    await openReviewsTab();
    await list();
    expect(within(itemOf('상품 2')).getByRole('button', { name: '답글 달기' })).toBeInTheDocument();
    expect(within(itemOf('상품 1')).getByRole('button', { name: '답글 수정' })).toBeInTheDocument();
    expect(within(itemOf('상품 1')).getByRole('button', { name: '답글 삭제' })).toBeInTheDocument();
  });

  it('답글을 달면 목록을 다시 불러와 답글이 보인다', async () => {
    state.userId = 7;
    state.reviews = [review(1)];
    await openReviewsTab();
    await list();
    await userEvent.click(screen.getByRole('button', { name: '답글 달기' }));
    await userEvent.type(screen.getByRole('textbox', { name: '답글' }), '구매해 주셔서 감사해요');
    await userEvent.click(screen.getByRole('button', { name: '답글 등록' }));
    expect(await screen.findByText('구매해 주셔서 감사해요')).toBeInTheDocument();
    expect(state.requests.find((r) => r.method === 'PUT')?.init?.body).toEqual({ content: '구매해 주셔서 감사해요' });
    expect(screen.getByRole('button', { name: '답글 수정' })).toBeInTheDocument();
  });

  it('빈 답글은 보내지 않고 입력 아래에 안내한다', async () => {
    state.userId = 7;
    state.reviews = [review(1)];
    await openReviewsTab();
    await list();
    await userEvent.click(screen.getByRole('button', { name: '답글 달기' }));
    await userEvent.click(screen.getByRole('button', { name: '답글 등록' }));
    expect(screen.getByText('답글 내용을 입력해 주세요.')).toBeInTheDocument();
    expect(state.requests.some((r) => r.method === 'PUT')).toBe(false);
  });

  it('답글을 수정하면 바뀐 내용으로 다시 보인다', async () => {
    state.userId = 7;
    state.reviews = [review(1, { reply: { content: '감사합니다!' } })];
    await openReviewsTab();
    await list();
    await userEvent.click(screen.getByRole('button', { name: '답글 수정' }));
    const input = screen.getByRole('textbox', { name: '답글 수정' });
    expect(input).toHaveValue('감사합니다!');
    await userEvent.clear(input);
    await userEvent.type(input, '다음에도 찾아 주세요');
    await userEvent.click(screen.getByRole('button', { name: '답글 저장' }));
    expect(await screen.findByText('다음에도 찾아 주세요')).toBeInTheDocument();
    expect(screen.queryByText('감사합니다!')).toBeNull();
  });

  it('답글을 삭제하면 확인 뒤 목록에서 사라지고 [답글 달기]로 바뀐다', async () => {
    state.userId = 7;
    state.reviews = [review(1, { reply: { content: '감사합니다!' } })];
    await openReviewsTab();
    await list();
    await userEvent.click(screen.getByRole('button', { name: '답글 삭제' }));
    await userEvent.click(screen.getByRole('button', { name: '삭제' }));
    expect(await screen.findByRole('button', { name: '답글 달기' })).toBeInTheDocument();
    expect(screen.queryByText('감사합니다!')).toBeNull();
    expect(state.requests.some((r) => r.method === 'DELETE' && r.path === '/api/v1/reviews/{reviewId}/reply')).toBe(true);
  });
});
