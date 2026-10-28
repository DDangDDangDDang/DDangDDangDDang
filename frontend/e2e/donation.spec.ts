import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 도네이션 마일리지
 * 1. 시청자가 방송 중 경매를 즉시 구매하고 → 판매자가 발송하고 → 시청자가 구매 확정하면 도네이션 마일리지가 적립된다. (헤더·도네이션 마일리지 화면)
 * 2. 시청자가 방송 화면에서 [선물하기]로 선물하면, 시청 화면과 판매자 스튜디오 영상 위에 "누구님이 몇 M을 선물했어요"가 뜬다.
 * 3. 판매자 프로필에 응원 지표가, 판매자 상점에 내 마일리지가 보인다. 관리자는 도네이션 정책 화면을 본다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 한다. 시청자 계정에 현금 포인트가 10,000P 이상 있어야 한다. (즉시 구매)
 * 계정: E2E_EMAIL / E2E_PASSWORD (시청자), E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자), E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD (관리자, 선택)
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };
const admin = { email: process.env.E2E_ADMIN_EMAIL ?? '', password: process.env.E2E_ADMIN_PASSWORD ?? '' };

const BUY_NOW_PRICE = 10_000;

type Envelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };
type Category = { id: number; children?: Category[] };

test.describe('도네이션 마일리지', () => {
  test.skip(!viewer.email || !seller.email, 'E2E_EMAIL, E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('구매 확정으로 도네이션 마일리지를 받아 방송 중인 판매자에게 선물하면 방송 화면에 선물 이펙트가 뜬다', async ({ browser, request }) => {
    test.setTimeout(150_000);
    // 화면 로그인 전에 API로 준비한다. (같은 계정으로 다시 로그인하면 이전 로그인이 끊긴다)
    const sellerToken = await apiLogin(request, seller);
    const viewerToken = await apiLogin(request, viewer);
    const cash = (await apiCall<{ balance: number }>(request, 'GET', '/api/v1/points/wallet', viewerToken)).body.data?.balance ?? 0;
    test.skip(cash < BUY_NOW_PRICE, `시청자 현금 포인트가 ${BUY_NOW_PRICE}P 이상 있어야 합니다. (지금 ${cash}P)`);

    await endActive(request, sellerToken);
    const title = `E2E 도네이션 ${Date.now()}`;
    const liveStreamId = (await expectOk(apiCall<{ id: number }>(request, 'POST', '/api/v1/live-streams', sellerToken, { title }), '방송 만들기')).id;
    await expectOk(apiCall(request, 'POST', `/api/v1/live-streams/${liveStreamId}/start`, sellerToken), '방송 시작');

    // 방송 중 경매를 즉시 구매 → 배송지 → 발송 → 구매 확정 (낙찰가의 1% = 100P 적립)
    const categories = (await apiCall<Category[]>(request, 'GET', '/api/v1/categories')).body.data ?? [];
    const leaf = categories.flatMap((c) => c.children ?? [])[0];
    test.skip(!leaf, '소분류 카테고리가 없어 상품을 만들 수 없습니다.');
    const product = await expectOk(
      apiCall<{ id: number }>(request, 'POST', '/api/v1/products', sellerToken, { categoryId: leaf?.id, name: `${title} 상품`, description: 'Playwright E2E 도네이션 테스트 상품입니다.' }),
      '상품 등록',
    );
    const auction = await expectOk(
      apiCall<{ id: number }>(request, 'POST', `/api/v1/lives/${liveStreamId}/auctions`, sellerToken, {
        productId: product.id,
        startPrice: 1_000,
        bidUnit: 1_000,
        buyNowPrice: BUY_NOW_PRICE,
        durationSec: 600,
      }),
      '방송 경매 등록',
    );
    const order = await expectOk(apiCall<{ orderId: number }>(request, 'POST', `/api/v1/auctions/${auction.id}/buy-now`, viewerToken), '즉시 구매');
    await expectOk(
      apiCall(request, 'PATCH', `/api/v1/orders/${order.orderId}/address`, viewerToken, { receiverName: '홍길동', receiverPhone: '01012345678', address: '서울시 강남구 테헤란로 1' }),
      '배송지 등록',
    );
    await expectOk(apiCall(request, 'PATCH', `/api/v1/orders/${order.orderId}/ship`, sellerToken, { courier: 'CJ', trackingNo: String(Date.now()).slice(-12) }), '발송');
    const before = (await apiCall<{ balance: number }>(request, 'GET', '/api/v1/donation-points/wallet', viewerToken)).body.data?.balance ?? 0;
    await expectOk(apiCall(request, 'PATCH', `/api/v1/orders/${order.orderId}/confirm`, viewerToken), '구매 확정');
    // 적립은 구매 확정이 커밋된 뒤 따로 된 스레드에서 한다.
    await expect
      .poll(async () => (await apiCall<{ balance: number }>(request, 'GET', '/api/v1/donation-points/wallet', viewerToken)).body.data?.balance ?? 0, {
        timeout: 10_000,
      })
      .toBe(before + 100);

    const sellerContext = await browser.newContext();
    const viewerContext = await browser.newContext();
    const studio = await sellerContext.newPage();
    const watch = await viewerContext.newPage();
    try {
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });

      // 시청자: 헤더에 도네이션 마일리지, 방송 화면의 [선물하기]
      await loginOnPage(watch, viewer);
      await expect(watch.getByRole('link', { name: `도네이션 마일리지 ${(before + 100).toLocaleString('ko-KR')} M` })).toBeVisible();
      await watch.goto(`/lives/${liveStreamId}`);
      await watch.getByRole('button', { name: '선물하기' }).click();
      const dialog = watch.getByRole('dialog', { name: /님에게 선물하기$/ });
      await expect(dialog.getByText('내 마일리지')).toBeVisible();
      await dialog.getByRole('button', { name: '100', exact: true }).click();
      await dialog.getByLabel('함께 보낼 한마디 (선택)').fill('E2E 응원합니다');
      await test.info().attach('선물 창', { body: await watch.screenshot(), contentType: 'image/png' });
      await dialog.getByRole('button', { name: '100 M 선물하기' }).click();
      await expect(dialog.getByText('100 M을 선물했어요. 방송 화면에 선물 알림이 떠요.')).toBeVisible();
      await dialog.getByRole('button', { name: '닫기' }).click();

      // 시청 화면·판매자 스튜디오 영상 위 선물 이펙트, 채팅 한 줄
      const effect = (page: Page) => page.getByRole('status').filter({ hasText: /님이 100 M을 선물했어요/ });
      await expect(effect(watch)).toBeVisible({ timeout: 10_000 });
      await expect(effect(watch)).toContainText('E2E 응원합니다');
      await test.info().attach('시청 화면 선물 이펙트', { body: await watch.screenshot(), contentType: 'image/png' });
      await expect(effect(studio)).toBeVisible({ timeout: 10_000 });
      await test.info().attach('스튜디오 선물 이펙트', { body: await studio.screenshot(), contentType: 'image/png' });
      await expect(watch.getByRole('list', { name: '채팅 메시지' }).getByText(/님이 100 M을 선물했어요/)).toBeVisible();

      // 도네이션 마일리지 화면: 선물한 내역
      await watch.goto('/me/donation-points');
      await expect(watch.getByRole('cell', { name: '선물함' }).first()).toBeVisible();
      await test.info().attach('도네이션 마일리지 화면', { body: await watch.screenshot(), contentType: 'image/png' });

      // 판매자 프로필: 응원 지표
      await watch.goto(`/lives/${liveStreamId}`);
      await watch.getByRole('button', { name: /판매자 프로필 보기/ }).click();
      const profile = watch.getByRole('dialog', { name: '판매자 프로필' });
      await expect(profile.getByText('응원')).toBeVisible();
      await expect(profile.getByText(/받음$/)).toBeVisible();
      await test.info().attach('판매자 프로필 응원 지표', { body: await watch.screenshot(), contentType: 'image/png' });

      // 판매자 상점: 내 마일리지
      await studio.goto('/me/donation-shop');
      await expect(studio.getByText('내 마일리지').first()).toBeVisible();
      await expect(studio.getByText('파워 판매자', { exact: true }).first()).toBeVisible();
      await test.info().attach('판매자 상점', { body: await studio.screenshot(), contentType: 'image/png' });
    } finally {
      await endActive(request, await apiLogin(request, seller).catch(() => ''));
      await sellerContext.close();
      await viewerContext.close();
    }
  });

  test('관리자는 도네이션 정책 화면에서 지금 정책과 바꾼 기록을 본다', async ({ page }) => {
    test.skip(!admin.email, 'E2E_ADMIN_EMAIL 환경변수가 필요합니다.');
    await loginOnPage(page, admin);
    await page.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '도네이션 정책' }).click();
    await expect(page.getByRole('heading', { name: '도네이션 정책' })).toBeVisible();
    await expect(page.getByLabel('적립 비율')).not.toHaveValue('');
    await expect(page.getByRole('heading', { name: '바꾼 기록' })).toBeVisible();
    await test.info().attach('도네이션 정책 화면', { body: await page.screenshot({ fullPage: true }), contentType: 'image/png' });
  });
});

async function loginOnPage(page: Page, account: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

async function apiCall<T>(request: APIRequestContext, method: 'GET' | 'POST' | 'PATCH', path: string, token?: string, data?: unknown) {
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
