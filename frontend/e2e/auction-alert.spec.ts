import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 경매 알림 받기: 시청자가 경매 상세에서 [알림 받기]를 누르고, 판매자가 그 경매를 취소하면
 * 화면 오른쪽 위 팝업과 알림함에 "경매가 취소됐어요"가 온다. 알림함에는 브라우저 알림 켜기도 있다.
 *
 * 계정: E2E_EMAIL / E2E_PASSWORD(시청자), E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD(판매자). 두 계정이 달라야 한다.
 * 브라우저 알림(웹 푸시) 실제 수신은 푸시 서비스가 필요해 E2E로 확인하지 않는다. (백엔드 WebPushSenderTest·WebPushCryptoTest)
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

type Envelope<T> = { data?: T; error?: { code?: string; message?: string } };
type Category = { id: number; children?: Category[] };

test.describe('경매 알림', () => {
  test.skip(!viewer.email || !seller.email || viewer.email === seller.email, '시청자와 판매자 계정(E2E_EMAIL, E2E_SELLER_EMAIL)이 따로 필요합니다.');

  test('알림 받기를 한 경매가 취소되면 팝업과 알림함으로 알려 준다', async ({ page, request }) => {
    const prepared = await prepareAuction(request);
    test.skip(!prepared, '소분류 카테고리가 없어 경매를 만들 수 없습니다. (/admin/categories)');
    if (!prepared) return;

    await login(page);
    await page.goto(`/auctions/${prepared.auctionId}`);
    const alertButton = page.getByRole('button', { name: '알림 받기' });
    await alertButton.click();
    const subscribed = page.getByRole('button', { name: '알림 받는 중' });
    await expect(subscribed).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByText('시작·마감 10분 전·취소·결과를 알려 드려요.')).toBeVisible();
    // 다시 들어와도 받는 중으로 보인다
    await page.reload();
    await expect(subscribed).toBeVisible();

    // 판매자가 취소하면 실시간 팝업이 뜨고, [경매 보기]로 그 경매로 간다
    const canceled = await call(request, 'POST', `/api/v1/auctions/${prepared.auctionId}/cancel`, prepared.token);
    expect(canceled.ok, `경매 취소 실패: ${canceled.body.error?.message}`).toBe(true);
    const toast = page.getByRole('status').filter({ hasText: '경매가 취소됐어요' });
    await expect(toast).toBeVisible();
    await expect(toast.getByText(prepared.name, { exact: false })).toBeVisible();
    await test.info().attach('경매 취소 알림 팝업', { body: await page.screenshot(), contentType: 'image/png' });

    // 알림함: 같은 알림과 브라우저 알림 켜기
    await page.getByRole('button', { name: /^알림/ }).first().click();
    const panel = page.getByRole('dialog', { name: '알림' });
    await expect(panel.getByRole('list', { name: '알림 목록' }).getByText('경매가 취소됐어요').first()).toBeVisible();
    await expect(panel.getByText(/브라우저 알림/)).toBeVisible();
    await test.info().attach('알림함', { body: await page.screenshot(), contentType: 'image/png' });

    // 취소된 경매는 알림 받기 버튼이 없다
    await panel.getByRole('list', { name: '알림 목록' }).getByRole('button').filter({ hasText: '경매가 취소됐어요' }).first().click();
    await expect(page).toHaveURL(new RegExp(`/auctions/${prepared.auctionId}$`));
    await expect(page.getByRole('button', { name: /알림 받/ })).toBeHidden();
  });
});

async function call<T>(request: APIRequestContext, method: 'GET' | 'POST', path: string, token?: string, data?: unknown) {
  const response = await request.fetch(path, { method, data, headers: token ? { Authorization: `Bearer ${token}` } : undefined });
  const body = (await response.json().catch(() => ({}))) as Envelope<T>;
  return { ok: response.ok(), body };
}

/** 판매자 계정으로 30분 뒤 시작하는 경매를 만든다. */
async function prepareAuction(request: APIRequestContext) {
  const login = await call<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, seller);
  const token = login.body.data?.accessToken ?? '';
  const categories = (await call<Category[]>(request, 'GET', '/api/v1/categories')).body.data ?? [];
  const leaf = categories.flatMap((c) => c.children ?? [])[0];
  if (!token || !leaf) return undefined;

  const name = `E2E 알림 경매 ${Date.now()}`;
  const product = await call<{ id: number }>(request, 'POST', '/api/v1/products', token, { categoryId: leaf.id, name, description: 'Playwright E2E 테스트가 만든 상품입니다.' });
  expect(product.ok, `상품 등록 실패: ${product.body.error?.message}`).toBe(true);
  const auction = await call<{ id: number }>(request, 'POST', '/api/v1/auctions', token, {
    productId: product.body.data?.id,
    startPrice: 10000,
    bidUnit: 1000,
    startAt: serverTime(30),
    endAt: serverTime(90),
  });
  expect(auction.ok, `경매 등록 실패: ${auction.body.error?.message}`).toBe(true);
  return { name, token, auctionId: auction.body.data?.id ?? 0 };
}

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(viewer.email);
  await page.getByLabel('비밀번호').fill(viewer.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

/** 지금부터 minutes분 뒤의 서버 시각 문자열 (시간대 없는 이 컴퓨터 시각) */
function serverTime(minutes: number) {
  const d = new Date(Date.now() + minutes * 60_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
}
