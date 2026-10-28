import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ShopPage } from './ShopPage';

type Request = { method: string; path: string; body?: { nickname?: string } };

// api.GET/POST는 요청 내용만 돌려주고, call()이 경로별로 정해 둔 응답을 준다.
const server = vi.hoisted(() => ({
  posts: [] as Request[],
  balance: 5_000,
  available: true,
  buyError: undefined as undefined | { code: string; message: string; status: number },
  replaceSession: vi.fn(),
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (method: string) => (path: string, init?: { body?: Request['body'] }) => ({ method, path, body: init?.body });
  return {
    ...original,
    api: { GET: request('GET'), POST: request('POST') },
    replaceSession: server.replaceSession,
    call: async (r: Request) => {
      if (r.path === '/api/v1/donation-points/wallet') return { balance: server.balance };
      if (r.path === '/api/v1/shop/items')
        return [
          {
            item: 'NICKNAME_CHANGE',
            name: '닉네임 변경권',
            description: '지금 바로 닉네임을 바꿔요.',
            price: 3_000,
            available: server.available,
            unavailableReason: server.available ? undefined : '지금은 변경권 없이 닉네임을 바꿀 수 있어요.',
          },
        ];
      if (r.path === '/api/v1/shop/nickname-change') {
        server.posts.push(r);
        if (server.buyError) throw new original.ApiError(server.buyError.code, server.buyError.message, server.buyError.status);
        return { accessToken: 'new-token', expiresIn: 1800, user: { userId: 1, nickname: r.body?.nickname } };
      }
      return undefined;
    },
  };
});

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ShopPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('ShopPage', () => {
  beforeEach(() => {
    server.posts = [];
    server.balance = 5_000;
    server.available = true;
    server.buyError = undefined;
    server.replaceSession.mockReset();
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('닉네임 변경권을 사면 새 닉네임을 보내고, 새 토큰으로 바꾼 뒤 성공을 알린다', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '닉네임 변경권 사기' }));
    const dialog = screen.getByRole('dialog', { name: '닉네임 변경권' });
    await userEvent.type(within(dialog).getByLabelText('새 닉네임'), '새닉네임');
    await userEvent.click(within(dialog).getByRole('button', { name: '3,000 M으로 바꾸기' }));

    expect(await screen.findByText('닉네임을 ‘새닉네임’(으)로 바꿨어요.')).toBeInTheDocument();
    expect(server.posts[0]?.body).toEqual({ nickname: '새닉네임' });
    expect(server.replaceSession).toHaveBeenCalledWith('new-token', { userId: 1, nickname: '새닉네임' });
  });

  it('지금은 살 수 없으면(변경 제한 기간이 아님) 사기 버튼을 끄고 이유를 보여준다', async () => {
    server.available = false;
    renderPage();

    expect(await screen.findByRole('button', { name: '닉네임 변경권 사기' })).toBeDisabled();
    expect(screen.getByText('지금은 변경권 없이 닉네임을 바꿀 수 있어요.')).toBeInTheDocument();
  });

  it('마일리지가 모자라면 사기 버튼을 끈다', async () => {
    server.balance = 2_000;
    renderPage();

    expect(await screen.findByText('마일리지가 부족해요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '닉네임 변경권 사기' })).toBeDisabled();
  });

  it('바꿀 수 없는 닉네임이면(USER_002) 창 안에 이유를 보여주고 창을 닫지 않는다', async () => {
    server.buyError = { code: 'USER_002', message: '이미 사용 중인 닉네임입니다.', status: 409 };
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '닉네임 변경권 사기' }));
    const dialog = screen.getByRole('dialog', { name: '닉네임 변경권' });
    await userEvent.type(within(dialog).getByLabelText('새 닉네임'), '남의닉네임');
    await userEvent.click(within(dialog).getByRole('button', { name: '3,000 M으로 바꾸기' }));

    expect(await within(dialog).findByText('이미 사용 중인 닉네임입니다.')).toBeInTheDocument();
    expect(server.replaceSession).not.toHaveBeenCalled();
  });
});
