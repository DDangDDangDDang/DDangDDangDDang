import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrderReviewsAdminPage } from './OrderReviewsAdminPage';

type Request = { method: string; path: string; params?: { path?: { orderId?: number } } };

const server = vi.hoisted(() => ({
  requests: [] as { method: string; path: string; params?: { path?: { orderId?: number } } }[],
  role: 'ADMIN' as 'ADMIN' | 'USER',
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => ({ accessToken: 'a', user: { userId: 1, nickname: '관리자', role: server.role } }),
    api: {
      GET: (path: string, init?: unknown) => ({ method: 'GET', path, ...(init as object) }),
      POST: (path: string, init?: unknown) => ({ method: 'POST', path, ...(init as object) }),
    },
    call: async (r: Request) => {
      server.requests.push(r);
      if (r.method === 'GET') {
        return {
          content: [
            {
              orderId: 7,
              orderNo: 'no-7',
              productName: '빈티지 카메라',
              finalPrice: 100000,
              settlementAmount: 95000,
              buyerId: 10,
              sellerId: 20,
              courier: 'CJ',
              trackingNo: '1234567890',
              shippedAt: '2026-10-28T10:00:00',
              reviewReason: 'BUYER_REPORTED',
              issueType: 'NOT_RECEIVED',
              issueDetail: '송장은 있는데 물건이 안 와요',
              reviewRequestedAt: '2026-11-04T10:00:00',
            },
          ],
          hasNext: false,
        };
      }
      return {};
    },
  };
});

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <OrderReviewsAdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

describe('OrderReviewsAdminPage', () => {
  beforeEach(() => {
    server.requests = [];
    server.role = 'ADMIN';
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('자동 확정이 멈춘 주문을 사유·신고 내용·송장과 함께 보여 준다', async () => {
    renderPage();

    expect(await screen.findByText('빈티지 카메라')).toBeInTheDocument();
    expect(screen.getByText('구매자 문제 신고')).toBeInTheDocument();
    expect(screen.getByText('상품을 받지 못했어요')).toBeInTheDocument();
    expect(screen.getByText('송장은 있는데 물건이 안 와요')).toBeInTheDocument();
    expect(screen.getByText(/1234567890/)).toBeInTheDocument();
  });

  it('[구매자 환불]은 확인 창을 거쳐 환불 API를 부른다', async () => {
    const user = renderPage();
    await user.click(await screen.findByRole('button', { name: '구매자 환불' }));

    const dialog = screen.getByRole('dialog', { name: '구매자에게 전액 환불할까요?' });
    await user.click(within(dialog).getByRole('button', { name: '환불' }));

    expect(server.requests.some((r) => r.method === 'POST' && r.path === '/api/v1/admin/orders/{orderId}/review/refund'
      && r.params?.path?.orderId === 7)).toBe(true);
  });

  it('관리자가 아니면 목록을 불러오지 않고 안내한다', () => {
    server.role = 'USER';
    renderPage();

    expect(screen.getByText('관리자만 쓸 수 있는 화면이에요')).toBeInTheDocument();
    expect(server.requests).toHaveLength(0);
  });
});
