import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { SanctionsAdminPage } from './SanctionsAdminPage';

type Request = { path: string; method: string; init?: { body?: { userId?: number; type?: string } } };

const state = vi.hoisted(() => ({
  role: 'ADMIN',
  released: false,
  posts: [] as { path: string; body?: { userId?: number; type?: string } }[],
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
        state.posts.push({ path: r.path, body: r.init?.body });
        state.released = true;
        return undefined;
      }
      return state.released
        ? []
        : [{ userId: 3, nickname: '잠수왕', type: 'BID_BAN', cancelCount: 4, bannedUntil: '2026-12-01T09:00:00' }];
    },
  };
});

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <SanctionsAdminPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('SanctionsAdminPage', () => {
  beforeAll(() => {
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  beforeEach(() => {
    state.role = 'ADMIN';
    state.released = false;
    state.posts = [];
  });

  it('관리자가 아니면 안내만 보여 준다', () => {
    state.role = 'USER';
    renderPage();

    expect(screen.getByText('관리자만 쓸 수 있는 화면이에요')).toBeInTheDocument();
  });

  it('검토 대상의 닉네임, 정지 종류, 취소 횟수를 보여 주고, 확인을 받아 정지를 푼다', async () => {
    renderPage();

    const list = await screen.findByRole('list', { name: '정지 검토 대상' });
    const row = within(list).getByRole('listitem');
    expect(row).toHaveTextContent('잠수왕');
    expect(row).toHaveTextContent('입찰 정지');
    expect(row).toHaveTextContent('4회');

    await userEvent.click(within(row).getByRole('button', { name: '정지 풀기' }));
    await userEvent.click(await screen.findByRole('button', { name: '풀기' }));

    expect(state.posts).toEqual([{ path: '/api/v1/admin/sanctions/release', body: { userId: 3, type: 'BID_BAN' } }]);
    expect(await screen.findByText('검토할 회원이 없어요')).toBeInTheDocument();
  });
});
