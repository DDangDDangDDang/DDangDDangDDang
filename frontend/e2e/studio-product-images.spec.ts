import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

/*
 * 상품 대표 이미지가 방송 쪽 화면에 나오는지
 * 1. 판매자 스튜디오 [경매 상품]: 대표 이미지, 10개씩 페이지, 카테고리로 거르기
 * 2. 시청 화면: 영상 위에 띄운 경매와 오른쪽 [이 방송의 경매]에 대표 이미지
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 하고, 소분류 카테고리가 하나 이상 있어야 한다.
 * 계정: E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자). 시청 화면은 손님으로 본다.
 */

const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

type Envelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };
type Category = { id: number; name: string; children?: Category[] };

test.describe('방송 화면의 상품 대표 이미지', () => {
  test.skip(!seller.email, 'E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('스튜디오 경매 상품 목록·영상 위 경매·이 방송의 경매에 대표 이미지가 나온다', async ({ browser, request }) => {
    test.setTimeout(120_000);
    const token = await apiLogin(request, seller);
    const categories = (await apiCall<Category[]>(request, 'GET', '/api/v1/categories')).body.data ?? [];
    const parents = categories.filter((c) => (c.children ?? []).length > 0);
    const leaf = parents[0]?.children?.[0];
    test.skip(!leaf, '소분류 카테고리가 없어 상품을 만들 수 없습니다.');

    await endActive(request, token);
    const title = `E2E 대표 이미지 ${Date.now()}`;
    const liveStreamId = (await expectOk(apiCall<{ id: number }>(request, 'POST', '/api/v1/live-streams', token, { title }), '방송 만들기')).id;
    await expectOk(apiCall(request, 'POST', `/api/v1/live-streams/${liveStreamId}/start`, token), '방송 시작');

    const sellerContext = await browser.newContext();
    const guestContext = await browser.newContext();
    const studio = await sellerContext.newPage();
    const watch = await guestContext.newPage();
    try {
      // 대표 이미지가 있는 상품을 하나 만들어 방송 경매로 열고 영상에 띄운다.
      const image = await uploadImage(request, token, await png(watch));
      const name = `${title} 상품`;
      const product = await expectOk(
        apiCall<{ id: number; thumbnailUrl?: string }>(request, 'POST', '/api/v1/products', token, { categoryId: leaf?.id, name, imageKeys: [image.key] }),
        '상품 등록',
      );
      expect(product.thumbnailUrl).toBe(image.url);
      const auction = await expectOk(
        apiCall<{ id: number; thumbnailUrl?: string }>(request, 'POST', `/api/v1/lives/${liveStreamId}/auctions`, token, {
          productId: product.id,
          startPrice: 1_000,
          bidUnit: 1_000,
          durationSec: 600,
        }),
        '방송 경매 등록',
      );
      expect(auction.thumbnailUrl).toBe(image.url);
      await expectOk(apiCall(request, 'PUT', `/api/v1/live-streams/${liveStreamId}/featured-auction`, token, { auctionId: auction.id }), '영상에 띄우기');

      // 스튜디오: 판매 중 상품만 10개씩, 대표 이미지, 카테고리 거르기
      const active = await apiCall<{ totalElements: number }>(request, 'GET', '/api/v1/users/me/products?status=ACTIVE&size=10', token);
      const total = active.body.data?.totalElements ?? 0;
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      const list = studio.getByRole('list', { name: '경매에 올릴 상품' });
      await expect(list).toBeVisible();
      await expect(list.locator(':scope > li')).toHaveCount(Math.min(total, 10));
      const pages = studio.getByRole('navigation', { name: '경매 상품 페이지' });
      if (total > 10) await expect(pages).toBeVisible();
      else await expect(pages).toBeHidden();
      // 방금 만든 상품은 최신순 첫 페이지 맨 위 (경매가 열려 '이 방송에서 경매 중')
      await expectLoaded(list.getByRole('img', { name }), image.url);

      await studio.getByLabel('카테고리로 거르기').selectOption(String(leaf?.id));
      await expect(list.getByRole('img', { name })).toBeVisible();
      await studio.screenshot({ path: 'test-results/studio-products.png', fullPage: true });
      const otherLeaf = parents.flatMap((p) => p.children ?? []).find((c) => c.id !== leaf?.id);
      if (otherLeaf) {
        await studio.getByLabel('카테고리로 거르기').selectOption(String(otherLeaf.id));
        await expect(list.getByRole('img', { name })).toBeHidden();
      }
      if (total > 10) {
        await studio.getByLabel('카테고리로 거르기').selectOption('');
        await pages.getByRole('button', { name: '다음 페이지' }).click();
        await expect(list.getByRole('img', { name })).toBeHidden();
      }

      // 스튜디오 [상품 등록] 창에서도 이미지를 올려 등록하면, 거르기가 풀린 첫 페이지 맨 위에 대표 이미지와 함께 나온다.
      await studio.getByRole('button', { name: '상품 등록' }).first().click();
      const dialog = studio.getByRole('dialog', { name: '상품 등록' });
      await dialog.getByLabel('카테고리').selectOption(String(leaf?.id));
      const dialogName = `${title} 창에서 등록`;
      await dialog.getByLabel('상품명 (50자 이하)').fill(dialogName);
      await dialog.getByLabel('상품 이미지 파일').setInputFiles({ name: 'studio.png', mimeType: 'image/png', buffer: await png(watch) });
      await expect(dialog.getByRole('list', { name: '상품 이미지 목록' }).getByRole('img')).toHaveCount(1);
      await dialog.getByRole('button', { name: '상품 등록' }).click();
      await expect(dialog).toBeHidden();
      await expect(studio.getByLabel('카테고리로 거르기')).toHaveValue('');
      await expect(list.getByRole('img', { name: dialogName })).toHaveAttribute('src', /product-images\//);

      // 시청 화면: 영상 위 경매와 이 방송의 경매
      await watch.goto(`/lives/${liveStreamId}`);
      const overlay = watch.getByRole('region', { name: '영상 위 경매' });
      await expectLoaded(overlay.getByRole('img', { name }), image.url);
      await expectLoaded(watch.getByRole('list', { name: '이 방송의 경매' }).getByRole('img', { name }), image.url);
      await watch.screenshot({ path: 'test-results/watch-auction-thumbnail.png' });
    } finally {
      await endActive(request, await apiLogin(request, seller).catch(() => ''));
      await sellerContext.close();
      await guestContext.close();
    }
  });
});

/** 이미지 주소가 맞고, 실제로 불러와졌는지 (읽기가 막혀 깨진 이미지면 naturalWidth가 0) */
async function expectLoaded(image: Locator, url: string) {
  await expect(image).toHaveAttribute('src', url);
  await expect.poll(() => image.evaluate((element) => (element as HTMLImageElement).naturalWidth)).toBeGreaterThan(0);
}

/** 상품 이미지 업로드: 업로드 URL을 받아 올린다. */
async function uploadImage(request: APIRequestContext, token: string, file: Buffer) {
  const signed = await expectOk(
    apiCall<{ uploadUrl: string; imageKey: string; imageUrl: string }>(request, 'POST', '/api/v1/products/image-uploads', token, {
      contentType: 'image/png',
      contentLength: file.length,
    }),
    '업로드 URL',
  );
  const put = await request.fetch(signed.uploadUrl, { method: 'PUT', headers: { 'Content-Type': 'image/png' }, data: file });
  expect(put.ok(), '이미지 올리기').toBe(true);
  return { key: signed.imageKey, url: signed.imageUrl };
}

async function png(page: Page) {
  await page.goto('/');
  const base64 = await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 400;
    canvas.height = 400;
    const context = canvas.getContext('2d');
    if (!context) return '';
    context.fillStyle = '#4c7cf5';
    context.fillRect(0, 0, 400, 400);
    context.fillStyle = '#fff';
    context.font = 'bold 120px sans-serif';
    context.fillText('대표', 70, 240);
    return canvas.toDataURL('image/png').split(',')[1] ?? '';
  });
  return Buffer.from(base64, 'base64');
}

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
