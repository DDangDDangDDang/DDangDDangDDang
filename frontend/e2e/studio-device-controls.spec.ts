import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

/*
 * 판매자 스튜디오: 마이크·카메라 켜기/끄기 버튼은 영상 미리보기 밖(아래 장치 선택 옆)에 있다.
 * 영상 위에 두면 시청자에게 보이는 경매 카드·박스를 가려 미리보기가 달라지기 때문이다.
 *
 * 로컬 백엔드가 떠 있어야 한다. 계정: E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자)
 */

const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

type Envelope<T> = { data?: T; error?: { message?: string } };

test.describe('스튜디오 장치 버튼', () => {
  test.skip(!seller.email, 'E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('켜기/끄기 버튼은 영상 아래 장치 선택 옆에 있고, 녹화 영상으로 바꿔도 영상 밖에 있다', async ({ page, request }) => {
    const token = await apiLogin(request, seller);
    await endActive(request, token);
    const created = await apiCall<{ id: number }>(request, 'POST', '/api/v1/live-streams', token, { title: `E2E 장치 버튼 ${Date.now()}` });
    expect(created.ok, `방송 만들기 실패: ${created.body.error?.message}`).toBe(true);

    try {
      await loginOnPage(page, seller);
      await page.goto('/studio');
      const preview = page.getByLabel('내 카메라 미리보기');
      await expect(preview).toBeVisible({ timeout: 20_000 });

      const camera = page.getByRole('button', { name: '카메라 끄기' });
      const mic = page.getByRole('button', { name: '마이크 끄기' });
      await expect(camera).toBeVisible();
      await expect(mic).toBeVisible();
      await expectBelow(preview, camera);
      await expectBelow(preview, mic);
      // 장치 선택과 같은 줄에 있다.
      await expectSameRow(page.getByLabel('카메라', { exact: true }), camera);
      await expectSameRow(page.getByLabel('마이크', { exact: true }), mic);
      await page.screenshot({ path: 'test-results/studio-device-controls.png', fullPage: true });

      // 버튼은 그대로 동작한다.
      await camera.click();
      await page.getByRole('button', { name: '카메라 켜기' }).click();
      await expect(page.getByRole('button', { name: '카메라 끄기' })).toBeVisible();

      // 녹화 영상으로 바꾸면 영상 화면·영상 소리 버튼이 파일 고르기 옆에 있다.
      await page.getByRole('radio', { name: '녹화 영상' }).click();
      const picture = page.getByRole('button', { name: /영상 화면 (켜기|끄기)/ });
      await expect(picture).toBeVisible();
      await expect(page.getByRole('button', { name: /영상 소리 (켜기|끄기)/ })).toBeVisible();
      await expectBelow(page.locator('.aspect-video').first(), picture);
      await page.getByRole('radio', { name: '카메라' }).click();
    } finally {
      await endActive(request, token);
    }
  });
});

async function expectBelow(top: Locator, below: Locator) {
  const a = await top.boundingBox();
  const b = await below.boundingBox();
  expect(a && b && b.y >= a.y + a.height, '버튼이 영상 미리보기 아래에 있어야 합니다').toBe(true);
}

async function expectSameRow(left: Locator, right: Locator) {
  const a = await left.boundingBox();
  const b = await right.boundingBox();
  expect(a && b && Math.abs(a.y + a.height - (b.y + b.height)) <= 2, '장치 선택과 같은 줄에 있어야 합니다').toBe(true);
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
