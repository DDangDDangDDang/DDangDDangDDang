import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ShopPage } from './ShopPage';

type Request = {
  method: string;
  path: string;
  body?: { nickname?: string; badge?: string; kind?: string; auctionId?: number };
  params?: { path?: { badge?: string } };
};

// api.GET/POST는 요청 내용만 돌려주고, call()이 경로별로 정해 둔 응답을 준다.
const server = vi.hoisted(() => ({
  posts: [] as Request[],
  balance: 5_000,
  available: true,
  buyError: undefined as undefined | { code: string; message: string; status: number },
  replaceSession: vi.fn(),
  badges: { owned: [] as string[], equipped: undefined as string | undefined, equippedTitle: undefined as string | undefined, equippedColor: undefined as string | undefined },
  boostError: undefined as undefined | { code: string; message: string; status: number },
  powerSellerReason: undefined as string | undefined,
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
            audience: 'BUYER',
            available: server.available,
            unavailableReason: server.available ? undefined : '지금은 변경권 없이 닉네임을 바꿀 수 있어요.',
          },
          {
            item: 'BADGE_STAR',
            name: '별',
            description: '닉네임 앞에 별이 붙어요.',
            price: 5_000,
            audience: 'BUYER',
            badge: 'STAR',
            badgeKind: 'SKIN',
            available: !server.badges.owned.includes('STAR'),
            unavailableReason: server.badges.owned.includes('STAR') ? '이미 가지고 있어요. 내 꾸미기에서 장착할 수 있어요.' : undefined,
          },
          {
            item: 'TITLE_BID_KING',
            name: '입찰왕',
            description: '닉네임 앞에 칭호가 붙어요.',
            price: 3_000,
            audience: 'BUYER',
            badge: 'TITLE_BID_KING',
            badgeKind: 'TITLE',
            available: true,
          },
          { item: 'COLOR_MINT', name: '민트', description: '닉네임이 민트색으로 보여요.', price: 2_000, audience: 'BUYER', badge: 'COLOR_MINT', badgeKind: 'COLOR', available: true },
          { item: 'AUCTION_BOOST', name: '경매 위로 올리기', description: '3시간 동안 맨 위에 올려요.', price: 1_000, audience: 'SELLER', available: true },
          {
            item: 'POWER_SELLER',
            name: '파워 판매자',
            description: '프로필과 방송 카드에 파워 표시가 영구로 붙어요.',
            price: 20_000,
            audience: 'SELLER',
            available: !server.powerSellerReason,
            unavailableReason: server.powerSellerReason,
          },
        ];
      if (r.path === '/api/v1/shop/power-seller') {
        server.posts.push(r);
        return undefined;
      }
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
        server.badges = { ...server.badges, owned: [...server.badges.owned, badge], equipped: badge === 'STAR' ? badge : server.badges.equipped };
        return server.badges;
      }
      if (r.path === '/api/v1/users/me/badge') {
        server.posts.push(r);
        if (r.body?.kind === 'TITLE') server.badges = { ...server.badges, equippedTitle: undefined };
        else server.badges = { ...server.badges, equipped: r.body?.badge };
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
    server.badges = { owned: [], equipped: undefined, equippedTitle: undefined, equippedColor: undefined };
    server.boostError = undefined;
    server.powerSellerReason = undefined;
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

  it('구매자 탭에는 꾸미기, 판매자 탭에는 판매자 상품만 보인다', async () => {
    renderPage();

    expect(await screen.findByRole('button', { name: /채팅 스킨/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '파워 판매자 사기' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('tab', { name: '판매자' }));

    expect(screen.getByRole('button', { name: '파워 판매자 사기' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '경매 위로 올리기 사기' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /채팅 스킨/ })).not.toBeInTheDocument();
    expect(screen.queryByText('내 꾸미기')).not.toBeInTheDocument();
  });

  it('채팅 스킨을 펼쳐 미리 보기로 고르고, 사면 바로 장착되어 내 꾸미기에 나온다', async () => {
    server.balance = 10_000;
    renderPage();

    expect(await screen.findByText('가진 꾸미기가 없어요')).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: '별' })).not.toBeInTheDocument();

    await userEvent.click(await screen.findByRole('button', { name: /채팅 스킨/ }));
    const option = screen.getByRole('radio', { name: '별' });
    expect(option.closest('label')?.querySelector('[data-badge="STAR"]')).toHaveTextContent('경매왕');
    expect(screen.getByRole('button', { name: '채팅 스킨 사기' })).toBeDisabled();

    await userEvent.click(option);
    await userEvent.click(screen.getByRole('button', { name: '채팅 스킨 사기' }));
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '5,000 M으로 사기' }));

    expect(await screen.findByText('‘별’을(를) 샀어요. 바로 장착했어요.')).toBeInTheDocument();
    expect(server.posts[0]?.params?.path?.badge).toBe('STAR');
    expect(within(screen.getByRole('list', { name: '내 채팅 스킨' })).getByRole('button', { name: '별 장착' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('채팅 뱃지는 닉네임 앞에 칭호로 미리 보여 준다', async () => {
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: /채팅 뱃지/ }));

    expect(screen.getByRole('radio', { name: '입찰왕' }).closest('label')?.querySelector('[data-title="TITLE_BID_KING"]')).toHaveTextContent('입찰왕');
  });

  it('스킨·뱃지·색상을 함께 장착해 보여 주고, 종류별로 바꿔 끼거나 뺄 수 있다', async () => {
    server.badges = { owned: ['STAR', 'HALO', 'TITLE_BID_KING', 'COLOR_MINT'], equipped: 'STAR', equippedTitle: 'TITLE_BID_KING', equippedColor: 'COLOR_MINT' };
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: '천사 링 장착' }));
    expect(await screen.findByRole('button', { name: '천사 링 장착', pressed: true })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '입찰왕 장착', pressed: true })).toBeInTheDocument();
    expect(server.posts[0]?.body).toEqual({ badge: 'HALO' });

    await userEvent.click(screen.getByRole('button', { name: '채팅 뱃지 빼기' }));
    expect(await screen.findByRole('button', { name: '채팅 뱃지 빼기' })).toBeDisabled();
    expect(server.posts[1]?.body).toEqual({ kind: 'TITLE' });
    expect(screen.getByRole('button', { name: '천사 링 장착', pressed: true })).toBeInTheDocument();
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

    await userEvent.click(await screen.findByRole('tab', { name: '판매자' }));
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

    await userEvent.click(await screen.findByRole('tab', { name: '판매자' }));
    await userEvent.click(await screen.findByRole('button', { name: '경매 위로 올리기 사기' }));
    const dialog = screen.getByRole('dialog', { name: '경매 위로 올리기' });
    await userEvent.click(await within(dialog).findByRole('radio', { name: /빈티지 카메라/ }));
    await userEvent.click(within(dialog).getByRole('button', { name: '1,000 M으로 올리기' }));

    expect(await within(dialog).findByText('지금은 위로 올릴 자리가 없어요. 약 30분 뒤에 올릴 수 있어요.')).toBeInTheDocument();
  });

  it('파워 판매자는 조건이 안 되면 사기 버튼을 끄고 이유를 보여 준다', async () => {
    server.powerSellerReason = '판매 확정 20건 이상부터 살 수 있어요. (지금 19건)';
    renderPage();
    await userEvent.click(await screen.findByRole('tab', { name: '판매자' }));
    expect(await screen.findByText('판매 확정 20건 이상부터 살 수 있어요. (지금 19건)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '파워 판매자 사기' })).toBeDisabled();
  });

  it('파워 판매자를 사면 성공을 알린다', async () => {
    server.balance = 30_000;
    renderPage();

    await userEvent.click(await screen.findByRole('tab', { name: '판매자' }));
    await userEvent.click(await screen.findByRole('button', { name: '파워 판매자 사기' }));
    await userEvent.click(screen.getByRole('button', { name: '20,000 M으로 사기' }));

    expect(await screen.findByText("파워 판매자가 되었어요. 판매자 프로필과 방송 카드에 '파워'가 붙어요.")).toBeInTheDocument();
    expect(server.posts[0]?.path).toBe('/api/v1/shop/power-seller');
  });
});
