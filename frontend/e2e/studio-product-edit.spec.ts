import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 방송 중 스튜디오 [경매 상품]에서 상품 고치기
 * - 경매가 없는 상품에는 [경매 시작] 옆에 [수정]이 있고, 창에서 고치면 목록에 바로 반영된다. (삭제 버튼은 없음)
 * - 예약된 경매·진행 중 경매가 있는 상품에는 [수정]이 없다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 하고, 소분류 카테고리가 하나 이상 있어야 한다.
 * 계정: E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자)
 */

const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

type Envelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };
type Category = { id: number; name: string; children?: Category[] };

test.describe('스튜디오 경매 상품 수정', () => {
  test.skip(!seller.email, 'E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('경매 없는 상품만 [수정]이 보이고, 창에서 고치면 목록에 바로 반영된다', async ({ page, request }) => {
    test.setTimeout(120_000);
    const token = await apiLogin(request, seller);
    const categories = (await apiCall<Category[]>(request, 'GET', '/api/v1/categories')).body.data ?? [];
    const leaf = categories.flatMap((c) => c.children ?? [])[0];
    test.skip(!leaf, '소분류 카테고리가 없어 상품을 만들 수 없습니다.');

    await endActive(request, token);
    const stamp = Date.now();
    const liveStreamId = (await expectOk(apiCall<{ id: number }>(request, 'POST', '/api/v1/live-streams', token, { title: `E2E 상품 수정 ${stamp}` }), '방송 만들기')).id;
    await expectOk(apiCall(request, 'POST', `/api/v1/live-streams/${liveStreamId}/start`, token), '방송 시작');

    // 끝나고 지울 예약 경매 (남겨 두면 한 시간 뒤 저절로 시작된다)
    let scheduledAuctionId: number | undefined;
    try {
      const create = (name: string) =>
        expectOk(apiCall<{ id: number }>(request, 'POST', '/api/v1/products', token, { categoryId: leaf?.id, name, description: '처음 설명' }), `상품 등록(${name})`);
      // 최신순이라 마지막에 만든 상품이 맨 위. 세 상품이 모두 첫 페이지에 들어온다.
      const scheduledName = `E2E 예약 ${stamp}`;
      const ongoingName = `E2E 진행 ${stamp}`;
      const plainName = `E2E 준비 ${stamp}`;
      const scheduled = await create(scheduledName);
      scheduledAuctionId = (
        await expectOk(
          apiCall<{ id: number }>(request, 'POST', '/api/v1/auctions', token, {
            productId: scheduled.id,
            startPrice: 1_000,
            bidUnit: 1_000,
            startAt: serverTime(60),
            endAt: serverTime(120),
          }),
          '예약 경매 등록',
        )
      ).id;
      const ongoing = await create(ongoingName);
      await expectOk(
        apiCall(request, 'POST', `/api/v1/lives/${liveStreamId}/auctions`, token, { productId: ongoing.id, startPrice: 1_000, bidUnit: 1_000, durationSec: 600 }),
        '방송 경매 등록',
      );
      const plain = await create(plainName);

      await loginOnPage(page, seller);
      await page.goto('/studio');
      const list = page.getByRole('list', { name: '경매에 올릴 상품' });
      const row = (name: string) => list.locator(':scope > li').filter({ hasText: name });
      await expect(row(plainName).getByRole('button', { name: '경매 시작' })).toBeVisible();

      // 경매가 있는 상품에는 [수정]이 없다. 삭제 버튼은 어디에도 없다.
      await expect(row(scheduledName).getByRole('button', { name: '이 방송에 올리기' })).toBeVisible();
      await expect(row(scheduledName).getByRole('button', { name: /수정/ })).toHaveCount(0);
      await expect(row(ongoingName).getByText('이 방송에서 경매 중')).toBeVisible();
      await expect(row(ongoingName).getByRole('button', { name: /수정/ })).toHaveCount(0);
      await expect(list.getByRole('button', { name: /삭제/ })).toHaveCount(0);

      // 판매 준비 중 상품 고치기: 창이 열리고 스튜디오를 떠나지 않는다.
      await row(plainName).getByRole('button', { name: `${plainName} 수정` }).click();
      const dialog = page.getByRole('dialog', { name: '상품 수정' });
      const nameInput = dialog.getByLabel('상품명 (50자 이하)');
      await expect(nameInput).toHaveValue(plainName);
      await expect(dialog.getByRole('button', { name: /삭제/ })).toHaveCount(0);
      const renamed = `${plainName} 고침`;
      await nameInput.fill(renamed);
      await page.screenshot({ path: 'test-results/studio-product-edit-dialog.png' });
      await dialog.getByRole('button', { name: '저장' }).click();
      await expect(dialog).toBeHidden();
      await expect(page).toHaveURL(/\/studio/);
      await expect(row(renamed)).toBeVisible();
      await page.screenshot({ path: 'test-results/studio-product-edited.png', fullPage: true });

      const saved = await expectOk(apiCall<{ name: string }>(request, 'GET', `/api/v1/products/${plain.id}`, token), '상품 조회');
      expect(saved.name).toBe(renamed);
    } finally {
      const cleanup = await apiLogin(request, seller).catch(() => '');
      if (cleanup && scheduledAuctionId) await apiCall(request, 'POST', `/api/v1/auctions/${scheduledAuctionId}/cancel`, cleanup);
      await endActive(request, cleanup);
    }
  });
});

/** 서버 시각(Asia/Seoul) 기준 n분 뒤. 서버는 시간대 없는 LocalDateTime을 받는다. */
function serverTime(minutes: number) {
  const d = new Date(Date.now() + minutes * 60_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
}

async function loginOnPage(page: Page, account: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

async function apiCall<T>(request: APIRequestContext, method: 'GET' | 'POST', path: string, token?: string, data?: unknown) {
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
