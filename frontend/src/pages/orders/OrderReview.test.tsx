import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrderDetailPage } from './OrderDetailPage';

type Request = { method: string; path: string; init?: { params?: { path?: { orderId?: number; reviewId?: number } }; body?: Record<string, unknown> } };

const state = vi.hoisted(() => ({
  status: 'SHIPPING' as 'SHIPPING' | 'COMPLETED',
  reviewed: false,
  requests: [] as Request[],
}));

vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (method: string) => (path: string, init: Request['init']) => ({ method, path, init });
  const order = () => ({
    orderId: 10,
    orderNo: 'O-10',
    buyerId: 1,
    sellerId: 7,
    productName: '필름 카메라',
    finalPrice: 50_000,
    status: state.status,
    receiverName: '홍길동',
    receiverPhone: '01012345678',
    address: '서울',
    courier: 'CJ',
    trackingNo: '123',
  });
  return {
    ...original,
    useSession: () => ({ accessToken: 't', user: { userId: 1 } }),
    api: { GET: request('GET'), PATCH: request('PATCH'), POST: request('POST'), DELETE: request('DELETE') },
    call: async (r: Request) => {
      state.requests.push(r);
      if (r.path === '/api/v1/orders/{orderId}') return order();
      if (r.path === '/api/v1/orders/{orderId}/tracking') return { steps: [] };
      if (r.path === '/api/v1/orders/{orderId}/confirm') {
        state.status = 'COMPLETED';
        return order();
      }
      if (r.path === '/api/v1/reviews' && r.method === 'POST') {
        if (state.reviewed) throw new original.ApiError('REVIEW_004', '이 부문에는 이미 리뷰를 작성을 했습니다', 409);
        state.reviewed = true;
        return { reviewId: 99, sellerId: 7, productName: '필름 카메라', ...r.init?.body, createdAt: '2026-10-29T10:00:00' };
      }
      return undefined;
    },
  };
});

function renderOrder() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/orders/10']}>
        <Routes>
          <Route path="/orders/:orderId" element={<OrderDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const reviewDialog = (name: string) => screen.findByRole('dialog', { name });
const posts = () => state.requests.filter((r) => r.method === 'POST' && r.path === '/api/v1/reviews');

describe('주문 리뷰 쓰기', () => {
  beforeEach(() => {
    state.status = 'SHIPPING';
    state.reviewed = false;
    state.requests = [];
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('구매 확정 전에는 [리뷰 쓰기] 버튼이 없다', async () => {
    renderOrder();
    expect(await screen.findByRole('button', { name: '구매 확정' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '리뷰 쓰기' })).toBeNull();
  });

  it('구매 확정 직후 "거래는 어떠셨나요?" 창이 뜨고, 건너뛰면 [리뷰 쓰기]로 나중에 쓸 수 있다', async () => {
    renderOrder();
    await userEvent.click(await screen.findByRole('button', { name: '구매 확정' }));
    const confirm = screen.getByRole('dialog', { name: '구매를 확정할까요?' });
    await userEvent.click(within(confirm).getByRole('button', { name: '구매 확정' }));

    const dialog = await reviewDialog('거래는 어떠셨나요?');
    await userEvent.click(within(dialog).getByRole('button', { name: '건너뛰기' }));
    expect(screen.queryByRole('dialog', { name: '거래는 어떠셨나요?' })).toBeNull();
    expect(screen.getByRole('button', { name: '리뷰 쓰기' })).toBeInTheDocument();
    expect(posts()).toHaveLength(0);
  });

  it('구매 확정한 주문은 처음 열 때 창 없이 [리뷰 쓰기] 버튼만 보인다', async () => {
    state.status = 'COMPLETED';
    renderOrder();
    expect(await screen.findByRole('button', { name: '리뷰 쓰기' })).toBeInTheDocument();
    expect(screen.queryByRole('dialog', { name: '거래는 어떠셨나요?' })).toBeNull();
  });

  it('별점을 고르지 않으면 보내지 않고, 별점만으로 리뷰를 쓸 수 있다', async () => {
    state.status = 'COMPLETED';
    renderOrder();
    await userEvent.click(await screen.findByRole('button', { name: '리뷰 쓰기' }));
    const dialog = await reviewDialog('리뷰 쓰기');
    await userEvent.click(within(dialog).getByRole('button', { name: '리뷰 남기기' }));
    expect(within(dialog).getByText('별점을 골라 주세요.')).toBeInTheDocument();
    expect(posts()).toHaveLength(0);

    await userEvent.click(within(dialog).getByRole('radio', { name: '4점' }));
    await userEvent.click(within(dialog).getByRole('button', { name: '리뷰 남기기' }));
    expect(posts()[0]?.init?.body).toEqual({ orderId: 10, rating: 4, content: undefined });
    // 쓴 리뷰가 주문 화면에 보이고 수정·삭제할 수 있다.
    expect(await screen.findByRole('img', { name: '별점 5점 만점에 4점' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '수정' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '리뷰 쓰기' })).toBeNull();
  });

  it('구매 확정 직후 창에서 별점과 내용을 남긴다', async () => {
    renderOrder();
    await userEvent.click(await screen.findByRole('button', { name: '구매 확정' }));
    await userEvent.click(within(screen.getByRole('dialog', { name: '구매를 확정할까요?' })).getByRole('button', { name: '구매 확정' }));
    const dialog = await reviewDialog('거래는 어떠셨나요?');
    await userEvent.click(within(dialog).getByRole('radio', { name: '5점' }));
    await userEvent.type(within(dialog).getByRole('textbox', { name: '내용 (선택)' }), '빠른 발송 감사합니다');
    await userEvent.click(within(dialog).getByRole('button', { name: '리뷰 남기기' }));
    expect(posts()[0]?.init?.body).toEqual({ orderId: 10, rating: 5, content: '빠른 발송 감사합니다' });
    expect(await screen.findByText('빠른 발송 감사합니다')).toBeInTheDocument();
  });

  it('이미 리뷰를 쓴 주문이면(REVIEW_004) "이미 리뷰를 작성했어요"라고 안내한다', async () => {
    state.status = 'COMPLETED';
    state.reviewed = true;
    renderOrder();
    await userEvent.click(await screen.findByRole('button', { name: '리뷰 쓰기' }));
    const dialog = await reviewDialog('리뷰 쓰기');
    await userEvent.click(within(dialog).getByRole('radio', { name: '3점' }));
    await userEvent.click(within(dialog).getByRole('button', { name: '리뷰 남기기' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('이미 리뷰를 작성했어요.');
    await userEvent.click(within(dialog).getByRole('button', { name: '취소' }));
    expect(screen.getByText(/이미 리뷰를 작성했어요\. 판매자 프로필의 리뷰 탭/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '리뷰 쓰기' })).toBeNull();
  });
});
