import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 방송 중 경매의 화면 연출
 * 1. 영상 위에는 경매를 2개까지 띄운다. 2개가 떠 있으면 스튜디오의 다른 경매 [영상에 띄우기]가 꺼져 있고, 누르면 하나를 내리라는 창이 뜬다.
 * 2. 누군가 입찰하면 시청 화면 영상 위와 채팅에 "누구님이 ‘상품’ 상품을 얼마에 입찰했습니다"가 뜬다. (직전 현재가보다 50% 이상 올리면 "몇 % 올림"과 화려한 연출)
 * 3. 즉시 구매로 팔리면 영상 위와 채팅에 "‘상품’ 상품이 누구님에게 얼마에 낙찰되었습니다"가 뜬다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 한다. 시청자 계정에 현금 포인트가 5,000P 이상 있어야 한다. (즉시 구매)
 * 계정: E2E_EMAIL / E2E_PASSWORD (시청자), E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자)
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

const BUY_NOW_PRICE = 5_000;

type Envelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };
type Category = { id: number; children?: Category[] };

test.describe('방송 중 경매 연출', () => {
  test.skip(!viewer.email || !seller.email, 'E2E_EMAIL, E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('경매는 2개까지 띄우고, 입찰·낙찰이 영상 위와 채팅에 안내된다', async ({ browser, request }) => {
    test.setTimeout(120_000);
    // 화면 로그인 전에 API로 준비한다. (같은 계정으로 다시 로그인하면 이전 로그인이 끊긴다) 시청 화면은 손님으로 본다.
    const sellerToken = await apiLogin(request, seller);
    const viewerToken = await apiLogin(request, viewer);
    const cash = (await apiCall<{ balance: number }>(request, 'GET', '/api/v1/points/wallet', viewerToken)).body.data?.balance ?? 0;
    test.skip(cash < BUY_NOW_PRICE, `시청자 현금 포인트가 ${BUY_NOW_PRICE}P 이상 있어야 합니다. (지금 ${cash}P)`);

    await endActive(request, sellerToken);
    const title = `E2E 경매 연출 ${Date.now()}`;
    const liveStreamId = (await expectOk(apiCall<{ id: number }>(request, 'POST', '/api/v1/live-streams', sellerToken, { title }), '방송 만들기')).id;
    await expectOk(apiCall(request, 'POST', `/api/v1/live-streams/${liveStreamId}/start`, sellerToken), '방송 시작');

    const categories = (await apiCall<Category[]>(request, 'GET', '/api/v1/categories')).body.data ?? [];
    const leaf = categories.flatMap((c) => c.children ?? [])[0];
    test.skip(!leaf, '소분류 카테고리가 없어 상품을 만들 수 없습니다.');
    const openAuction = async (name: string) => {
      const product = await expectOk(
        apiCall<{ id: number }>(request, 'POST', '/api/v1/products', sellerToken, {
          categoryId: leaf?.id,
          name,
          description: 'Playwright E2E 경매 연출 테스트 상품입니다.',
        }),
        '상품 등록',
      );
      return expectOk(
        apiCall<{ id: number }>(request, 'POST', `/api/v1/lives/${liveStreamId}/auctions`, sellerToken, {
          productId: product.id,
          startPrice: 1_000,
          bidUnit: 1_000,
          buyNowPrice: BUY_NOW_PRICE,
          durationSec: 600,
        }),
        '방송 경매 등록',
      );
    };
    const names = ['가', '나', '다'].map((suffix) => `${title} ${suffix}`);
    const [first, second, third] = [await openAuction(names[0] ?? ''), await openAuction(names[1] ?? ''), await openAuction(names[2] ?? '')];

    // 2개까지는 띄워지고, 세 번째는 서버도 막는다.
    for (const auction of [first, second]) {
      await expectOk(apiCall(request, 'PUT', `/api/v1/live-streams/${liveStreamId}/featured-auction`, sellerToken, { auctionId: auction.id }), '영상에 띄우기');
    }
    const blocked = await apiCall(request, 'PUT', `/api/v1/live-streams/${liveStreamId}/featured-auction`, sellerToken, { auctionId: third.id });
    expect(blocked.body.error?.code).toBe('LIVESTREAM_017');

    const sellerContext = await browser.newContext();
    const guestContext = await browser.newContext();
    const studio = await sellerContext.newPage();
    const watch = await guestContext.newPage();
    try {
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });
      const thirdButton = studio.getByRole('button', { name: `${names[2]} 영상에 띄우기` });
      await expect(thirdButton).toHaveAttribute('aria-disabled', 'true');
      // 꺼진 모양(aria-disabled)이지만 누르면 이유를 알려 준다. Playwright는 aria-disabled 버튼을 누르지 않고 기다리므로 force로 누른다.
      await thirdButton.click({ force: true });
      const limit = studio.getByRole('dialog', { name: '영상 위에는 경매를 2개까지 띄울 수 있어요' });
      await expect(limit).toBeVisible();
      await test.info().attach('스튜디오 (2개 제한 안내)', { body: await studio.screenshot(), contentType: 'image/png' });
      await limit.getByRole('button', { name: '확인' }).click();
      await expect(limit).toBeHidden();

      await watch.goto(`/lives/${liveStreamId}`);
      await expect(watch.getByRole('region', { name: '영상 위 경매' })).toHaveCount(2);
      const chat = watch.getByRole('list', { name: '채팅 메시지' });
      // 화면을 연 뒤에 온 메시지만 이펙트로 띄우므로, 채팅 연결을 기다린다.
      await expect(watch.getByPlaceholder(/채팅을 입력하세요/)).toBeEnabled({ timeout: 15_000 });

      // 첫 입찰은 시작가(1,000P)가 직전 가격이다. 2배(2,000P)로 입찰해 50% 이상 단계를 본다. (같은 사람은 연이어 입찰할 수 없다)
      await expectOk(apiCall(request, 'POST', `/api/v1/auctions/${first.id}/bids`, viewerToken, { amount: 2_000 }), '입찰');
      await expect(chat.getByText(`‘${names[0]}’ 상품을 2,000P에 입찰했습니다.`, { exact: false })).toBeVisible();
      const bidEffect = watch.locator('[data-tier="up50"]');
      await expect(bidEffect).toBeVisible({ timeout: 15_000 });
      await expect(bidEffect).toContainText(`‘${names[0]}’ 상품을 2,000 P에 입찰했습니다.`);
      await expect(bidEffect).toContainText('100% 올림');
      await test.info().attach('시청 화면 (2배로 올린 입찰)', { body: await watch.screenshot(), contentType: 'image/png' });

      await expectOk(apiCall(request, 'POST', `/api/v1/auctions/${first.id}/buy-now`, viewerToken), '즉시 구매');
      const soldText = `‘${names[0]}’ 상품이`;
      await expect(chat.getByText(soldText, { exact: false })).toContainText('5,000P에 낙찰되었습니다.');
      const soldEffect = watch.getByRole('status').filter({ hasText: '낙찰되었습니다' });
      await expect(soldEffect).toBeVisible();
      await expect(soldEffect).toContainText(`${soldText}`);
      await test.info().attach('시청 화면 (낙찰)', { body: await watch.screenshot(), contentType: 'image/png' });
      // 낙찰된 경매는 영상 위에서 내려가 하나만 남는다.
      await expect(watch.getByRole('region', { name: '영상 위 경매' })).toHaveCount(1);
    } finally {
      await endActive(request, await apiLogin(request, seller).catch(() => ''));
      await sellerContext.close();
      await guestContext.close();
    }
  });
});

async function loginOnPage(page: Page, account: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

async function apiCall<T>(request: APIRequestContext, method: 'GET' | 'POST' | 'PUT', path: string, token?: string, data?: unknown) {
  const response = await request.fetch(path, { method, data, headers: token ? { Authorization: `Bearer ${token}` } : undefined });
  const body = (await response.json().catch(() => ({}))) as Envelope<T>;
  return { ok: response.ok(), body };
}

async function expectOk<T>(call: Promise<{ ok: boolean; body: Envelope<T> }>, what: string): Promise<T> {
  const result = await call;
  expect(result.ok, `${what} 실패: ${result.body.error?.message}`).toBe(true);
  return result.body.data as T;
}

async function apiLogin(request: APIRequestContext, account: { email: string; password: string }) {
  const login = await apiCall<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, account);
  expect(login.ok, `로그인 실패: ${login.body.error?.message}`).toBe(true);
  return login.body.data?.accessToken ?? '';
}

/** 판매자의 종료되지 않은 방송(대기 중·방송 중)을 끝낸다. */
async function endActive(request: APIRequestContext, token: string) {
  if (!token) return;
  const active = (await apiCall<{ id?: number }>(request, 'GET', '/api/v1/live-streams/me/active', token)).body.data;
  if (active?.id) await apiCall(request, 'POST', `/api/v1/live-streams/${active.id}/end`, token);
}
