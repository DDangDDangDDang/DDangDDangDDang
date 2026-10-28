import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ShopPage } from './ShopPage';

type Request = {
  method: string;
  path: string;
  body?: { nickname?: string; badge?: string; auctionId?: number };
  params?: { path?: { badge?: string } };
};

// api.GET/POST는 요청 내용만 돌려주고, call()이 경로별로 정해 둔 응답을 준다.
const server = vi.hoisted(() => ({
  posts: [] as Request[],
  balance: 5_000,
  available: true,
  buyError: undefined as undefined | { code: string; message: string; status: number },
  replaceSession: vi.fn(),
  badges: { owned: [] as string[], equipped: undefined as string | undefined },
  boostError: undefined as undefined | { code: string; message: string; status: number },
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (method: string) => (path: string, init?: { body?: Request['body']; params?: Request['params'] }) => ({
    method,
    path,
    body: init?.body,
    params: init?.params,
  });
  return {
    ...original,
    api: { GET: request('GET'), POST: request('POST'), PUT: request('PUT') },
    replaceSession: server.replaceSession,
    useSession: () => ({ user: { userId: 1, nickname: '경매왕', role: 'USER' } }),
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
          {
            item: 'BADGE_STAR',
            name: '별 뱃지',
            description: '닉네임 앞에 별이 붙어요.',
            price: 5_000,
            badge: 'STAR',
            available: !server.badges.owned.includes('STAR'),
            unavailableReason: server.badges.owned.includes('STAR') ? '이미 가지고 있어요. 내 뱃지에서 장착할 수 있어요.' : undefined,
          },
          { item: 'AUCTION_BOOST', name: '경매 위로 올리기', description: '3시간 동안 맨 위에 올려요.', price: 1_000, available: true },
        ];
      if (r.path === '/api/v1/users/me/badges') return server.badges;
      if (r.path === '/api/v1/users/me/auctions')
        return [
          { id: 11, productName: '빈티지 카메라', status: 'ONGOING', endAt: '2026-10-29T20:00:00' },
          { id: 12, productName: '레코드판', status: 'READY', endAt: '2026-10-30T20:00:00' },
        ];
      if (r.path === '/api/v1/shop/auction-boosts') {
        server.posts.push(r);
        if (server.boostError) throw new original.ApiError(server.boostError.code, server.boostError.message, server.boostError.status);
        return { auctionId: r.body?.auctionId, startsAt: '2026-10-28T15:00:00', endsAt: '2026-10-28T18:00:00' };
      }
      if (r.path === '/api/v1/shop/badges/{badge}') {
        server.posts.push(r);
        const badge = r.params?.path?.badge ?? '';
        server.badges = { owned: [...server.badges.owned, badge], equipped: badge };
        return server.badges;
      }
      if (r.path === '/api/v1/users/me/badge') {
        server.posts.push(r);
        server.badges = { ...server.badges, equipped: r.body?.badge };
        return server.badges;
      }
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
    server.badges = { owned: [], equipped: undefined };
    server.boostError = undefined;
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

    expect(await screen.findAllByText('마일리지가 부족해요')).toHaveLength(2);
    expect(screen.getByRole('button', { name: '닉네임 변경권 사기' })).toBeDisabled();
  });

  it('뱃지 상품은 내 닉네임을 꾸민 미리 보기를 보여 주고, 사면 바로 장착되어 내 뱃지에 나온다', async () => {
    server.balance = 10_000;
    renderPage();

    expect(await screen.findByText('가진 뱃지가 없어요')).toBeInTheDocument();
    const row = (await screen.findByText('별 뱃지')).closest('li')!;
    expect(row.querySelector('[data-badge="STAR"]')).toHaveTextContent('경매왕');

    await userEvent.click(screen.getByRole('button', { name: '별 뱃지 사기' }));
    await userEvent.click(screen.getByRole('button', { name: '5,000 M으로 사기' }));

    expect(await screen.findByText('별 뱃지를 샀어요. 바로 장착했어요.')).toBeInTheDocument();
    expect(server.posts[0]?.params?.path?.badge).toBe('STAR');
    expect(within(screen.getByRole('list', { name: '내 뱃지' })).getByRole('button', { name: '별 장착' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('내 뱃지에서 다른 뱃지로 바꿔 끼거나 뺄 수 있다', async () => {
    server.badges = { owned: ['STAR', 'HALO'], equipped: 'STAR' };
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '천사 링 장착' }));
    expect(await screen.findByRole('button', { name: '천사 링 장착', pressed: true })).toBeInTheDocument();
    expect(server.posts[0]?.body).toEqual({ badge: 'HALO' });

    await userEvent.click(screen.getByRole('button', { name: '뱃지 빼기' }));
    expect(await screen.findByRole('button', { name: '뱃지 빼기' })).toBeDisabled();
    expect(server.posts[1]?.body).toEqual({ badge: undefined });
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

  it('경매 위로 올리기는 내 경매를 골라 올리고, 언제까지 올라가는지 알려 준다', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '경매 위로 올리기 사기' }));
    const dialog = screen.getByRole('dialog', { name: '경매 위로 올리기' });
    const submit = within(dialog).getByRole('button', { name: '1,000 M으로 올리기' });
    expect(submit).toBeDisabled();
    await userEvent.click(await within(dialog).findByRole('radio', { name: /레코드판/ }));
    await userEvent.click(submit);

    expect(await screen.findByText(/‘레코드판’ 경매를 .*까지 경매 목록 맨 위에 올렸어요\./)).toBeInTheDocument();
    expect(server.posts[0]?.body).toEqual({ auctionId: 12 });
  });

  it('자리가 없으면(SHOP_003) 창 안에 서버 안내를 그대로 보여 준다', async () => {
    server.boostError = { code: 'SHOP_003', message: '지금은 위로 올릴 자리가 없어요. 약 30분 뒤에 올릴 수 있어요.', status: 409 };
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '경매 위로 올리기 사기' }));
    const dialog = screen.getByRole('dialog', { name: '경매 위로 올리기' });
    await userEvent.click(await within(dialog).findByRole('radio', { name: /빈티지 카메라/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: '1,000 M으로 올리기' }));

    expect(await within(dialog).findByText('지금은 위로 올릴 자리가 없어요. 약 30분 뒤에 올릴 수 있어요.')).toBeInTheDocument();
  });
});
