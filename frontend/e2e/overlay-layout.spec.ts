import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 방송 화면 배치
 * 판매자가 스튜디오에서 [화면 배치 편집]을 눌러 경매 카드 견본을 오른쪽 위로 끌어 저장하면, 시청 화면에서도 경매 카드가 오른쪽 위에 나온다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 한다.
 * 계정: E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자)
 */

const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

type Envelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };
type Category = { id: number; children?: Category[] };

test.describe('방송 화면 배치', () => {
  test.skip(!seller.email, 'E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('스튜디오에서 경매 카드를 끌어 옮기고 저장하면 시청 화면에도 그 자리에 나온다', async ({ browser, request }) => {
    test.setTimeout(120_000);
    const sellerToken = await apiLogin(request, seller);
    await endActive(request, sellerToken);
    const title = `E2E 화면 배치 ${Date.now()}`;
    const liveStreamId = (await expectOk(apiCall<{ id: number }>(request, 'POST', '/api/v1/live-streams', sellerToken, { title }), '방송 만들기')).id;
    await expectOk(apiCall(request, 'POST', `/api/v1/live-streams/${liveStreamId}/start`, sellerToken), '방송 시작');
    const categories = (await apiCall<Category[]>(request, 'GET', '/api/v1/categories')).body.data ?? [];
    const leaf = categories.flatMap((c) => c.children ?? [])[0];
    test.skip(!leaf, '소분류 카테고리가 없어 상품을 만들 수 없습니다.');
    const product = await expectOk(
      apiCall<{ id: number }>(request, 'POST', '/api/v1/products', sellerToken, {
        categoryId: leaf?.id,
        name: `${title} 상품`,
        description: 'Playwright E2E 화면 배치 테스트 상품입니다.',
      }),
      '상품 등록',
    );
    const auction = await expectOk(
      apiCall<{ id: number }>(request, 'POST', `/api/v1/lives/${liveStreamId}/auctions`, sellerToken, {
        productId: product.id,
        startPrice: 1_000,
        bidUnit: 1_000,
        durationSec: 600,
      }),
      '방송 경매 등록',
    );
    await expectOk(apiCall(request, 'PUT', `/api/v1/live-streams/${liveStreamId}/featured-auction`, sellerToken, { auctionId: auction.id }), '영상에 띄우기');

    const sellerContext = await browser.newContext();
    const guestContext = await browser.newContext();
    const studio = await sellerContext.newPage();
    const watch = await guestContext.newPage();
    try {
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });

      await studio.getByRole('button', { name: '화면 배치 편집' }).click();
      const handle = studio.getByRole('button', { name: '경매 카드 위치 옮기기' });
      await expect(handle).toBeVisible();
      for (const label of ['선물 안내', '입찰 안내', '낙찰 안내']) {
        await expect(studio.getByRole('button', { name: `${label} 위치 옮기기` })).toBeVisible();
      }
      // 견본(기본: 왼쪽 아래)을 오른쪽 위 끝 너머까지 끈다. 배치 영역 끝에서 멈춘다.
      const box = await handle.boundingBox();
      if (!box) throw new Error('경매 카드 견본을 찾지 못했습니다.');
      await studio.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
      await studio.mouse.down();
      await studio.mouse.move(box.x + 2_000, box.y - 2_000, { steps: 8 });
      await studio.mouse.up();
      await test.info().attach('스튜디오 (화면 배치 편집)', { body: await studio.screenshot(), contentType: 'image/png' });
      await studio.getByRole('button', { name: '저장' }).click();
      await expect(studio.getByRole('button', { name: '화면 배치 편집' })).toBeVisible();

      const saved = (await apiCall<{ overlayLayout?: { featured?: { x: number; y: number } } }>(request, 'GET', `/api/v1/live-streams/${liveStreamId}`)).body
        .data;
      expect(saved?.overlayLayout?.featured).toEqual({ x: 100, y: 0 });

      // 시청 화면: 경매 카드가 영상 오른쪽 위에 있다.
      await watch.goto(`/lives/${liveStreamId}`);
      const card = watch.getByRole('region', { name: '영상 위 경매' });
      await expect(card).toBeVisible();
      const stage = await watch.locator('section[aria-label="방송"] .aspect-video').first().boundingBox();
      const cardBox = await card.boundingBox();
      if (!stage || !cardBox) throw new Error('영상이나 경매 카드를 찾지 못했습니다.');
      expect(cardBox.x + cardBox.width).toBeGreaterThan(stage.x + stage.width - 40);
      expect(cardBox.y).toBeLessThan(stage.y + stage.height / 3);
      await test.info().attach('시청 화면 (오른쪽 위 경매 카드)', { body: await watch.screenshot(), contentType: 'image/png' });
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
