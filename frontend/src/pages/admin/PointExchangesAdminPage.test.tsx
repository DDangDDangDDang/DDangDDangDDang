import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { PointExchangesAdminPage } from './PointExchangesAdminPage';

type Request = { path: string; method: string; init?: { params?: { path?: { exchangeId?: number } }; body?: { reason?: string } } };

// 신청됨 1건. 송금 시작 → 송금 중, 송금 완료 → 완료, 반려 → 반려됨으로 바뀐다.
const state = vi.hoisted(() => ({
  role: 'ADMIN',
  status: 'PENDING',
  posts: [] as { path: string; reason?: string }[],
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => ({ accessToken: 't', user: { userId: 1, role: state.role } }),
    api: {
      GET: (path: string) => ({ path, method: 'GET' }),
      POST: (path: string, init: Request['init']) => ({ path, method: 'POST', init }),
    },
    call: async (r: Request) => {
      if (r.method === 'POST') {
        state.posts.push({ path: r.path, reason: r.init?.body?.reason });
        if (r.path.endsWith('/start')) state.status = 'PROCESSING';
        if (r.path.endsWith('/complete')) state.status = 'COMPLETE';
        if (r.path.endsWith('/reject')) state.status = 'FAILED';
        return {};
      }
      const row = {
        exchangeId: 7,
        userId: 3,
        nickname: '판매왕',
        email: 'seller@ddang.com',
        amount: 50_000,
        status: state.status,
        bankCode: '088',
        accountNumber: '110123456789',
        accountHolder: '홍길동',
        createdAt: '2026-10-26T10:00:00',
      };
      // 보기 필터(신청됨)와 상관없이 한 건을 돌려준다. 화면은 상태에 맞는 버튼만 그린다.
      return { content: [row], hasNext: false };
    },
  };
});

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <PointExchangesAdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PointExchangesAdminPage', () => {
  beforeAll(() => {
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('관리자가 아니면 목록을 보여주지 않는다', () => {
    state.role = 'USER';
    renderPage();
    expect(screen.getByText('관리자만 쓸 수 있는 화면이에요')).toBeInTheDocument();
    state.role = 'ADMIN';
  });

  it('송금 시작 → 송금 완료 순서로 처리한다', async () => {
    state.status = 'PENDING';
    state.posts = [];
    renderPage();

    const list = await screen.findByRole('list');
    expect(within(list).getByText('110123456789')).toBeInTheDocument();
    expect(within(list).queryByRole('button', { name: '송금 완료' })).not.toBeInTheDocument();

    await userEvent.click(within(list).getByRole('button', { name: '송금 시작' }));
    await userEvent.click(within(await screen.findByRole('dialog', { name: '송금을 시작할까요?' })).getByRole('button', { name: '송금 시작' }));
    expect(state.posts.map((p) => p.path)).toEqual(['/api/v1/admin/point-exchanges/{exchangeId}/start']);

    await userEvent.click(await within(list).findByRole('button', { name: '송금 완료' }));
    await userEvent.click(within(await screen.findByRole('dialog', { name: '이체를 마쳤나요?' })).getByRole('button', { name: '송금 완료' }));
    expect(state.posts.at(-1)?.path).toBe('/api/v1/admin/point-exchanges/{exchangeId}/complete');
  });

  it('반려는 사유를 적어야 보낼 수 있다', async () => {
    state.status = 'PENDING';
    state.posts = [];
    renderPage();

    await userEvent.click(within(await screen.findByRole('list')).getByRole('button', { name: '반려' }));
    const dialog = await screen.findByRole('dialog', { name: '환전 반려' });
    const submit = within(dialog).getByRole('button', { name: '반려하기' });
    expect(submit).toBeDisabled();

    await userEvent.type(within(dialog).getByLabelText('반려 사유'), '예금주 불일치');
    await userEvent.click(submit);
    expect(state.posts).toEqual([{ path: '/api/v1/admin/point-exchanges/{exchangeId}/reject', reason: '예금주 불일치' }]);
  });
});
