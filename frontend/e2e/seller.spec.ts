import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 판매자 구독과 방송 시작 알림
 * 1. 시청자가 방송 화면에서 판매자를 구독하면 구독자 수가 늘고, 홈 왼쪽 "구독 중인 판매자"에 LIVE로 나온다. LIVE를 누르면 그 방송으로 간다.
 * 2. 구독한 판매자가 새 방송을 시작하면 시청자 화면 구석에 알림 팝업이 뜨고, 종 아이콘 알림함에도 쌓인다. 알림을 누르면 그 방송으로 간다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 한다. 판매자 방송은 API로 만들고 시작한다. (화면 송출은 live-watch.spec.ts)
 * 계정: E2E_EMAIL / E2E_PASSWORD (시청자), E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자)
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

type Envelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };

test.describe('판매자 구독', () => {
  test.skip(!viewer.email || !seller.email, 'E2E_EMAIL, E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('방송 화면에서 구독하면 홈 목록에 LIVE로 나오고, 판매자가 새 방송을 시작하면 알림이 온다', async ({ page, request }) => {
    test.setTimeout(90_000);
    const sellerToken = await apiLogin(request);
    await endActive(request, sellerToken);
    const first = await startLive(request, sellerToken, `E2E 구독 방송 ${Date.now()}`);

    try {
      await login(page);
      // 구독 여부는 판매자 정보를 불러온 뒤에 정해진다. 그 전에 잠깐 보이는 [구독]으로 판단하지 않도록 응답을 기다린다.
      const profileLoaded = page.waitForResponse((r) => /\/api\/v1\/sellers\/\d+\/profile/.test(r.url()) && r.ok());
      await page.goto(`/lives/${first}`);
      await profileLoaded;
      const subscribe = page.getByRole('button', { name: '구독', exact: true });
      const unsubscribe = page.getByRole('button', { name: '구독 취소' });
      await expect(subscribe.or(unsubscribe)).toBeVisible();
      // 남아 있던 구독은 먼저 취소하고 다시 구독한다.
      if (await unsubscribe.isVisible()) {
        await unsubscribe.click();
        await expect(subscribe).toBeVisible();
      }
      const before = await subscriberCount(page);
      await subscribe.click();
      await expect(unsubscribe).toBeVisible();
      await expect.poll(() => subscriberCount(page)).toBe(before + 1);

      // 인증 판매자는 이름 옆에 인증 마크가 붙고, 누르면 프로필 창(기본 정보·경매 기록)이 열린다.
      // E2E 계정에는 관리자가 없어 승인하지 못하므로, 프로필 응답만 인증된 것으로 바꿔 화면을 확인한다. (승인 흐름은 백엔드 테스트)
      await page.route('**/api/v1/sellers/*/profile', async (route) => {
        const response = await route.fetch();
        const json = (await response.json()) as Envelope<Record<string, unknown>>;
        json.data = { ...json.data, verifiedSeller: true, storeName: 'E2E 상점', introduction: 'Playwright E2E 테스트용 판매자 소개입니다.' };
        await route.fulfill({ response, json });
      });
      await page.reload();
      await expect(page.getByRole('img', { name: '판매자 인증' }).first()).toBeVisible();
      await page.getByRole('button', { name: /판매자 프로필 보기/ }).click();
      const profile = page.getByRole('dialog', { name: '판매자 프로필' });
      await expect(profile.getByText('E2E 상점')).toBeVisible();
      await expect(profile.getByText('방송한 시간')).toBeVisible();
      await expect(profile.getByText('거래 완료')).toBeVisible();
      await expect(profile.getByRole('button', { name: '구독 취소' })).toBeVisible();
      await test.info().attach('판매자 프로필 (기본 정보)', { body: await page.screenshot(), contentType: 'image/png' });
      await profile.getByRole('tab', { name: '경매 기록' }).click();
      await expect(profile.getByRole('list', { name: '경매 기록' }).or(profile.getByText('아직 진행한 경매가 없어요'))).toBeVisible();
      await test.info().attach('판매자 프로필 (경매 기록)', { body: await page.screenshot(), contentType: 'image/png' });
      await profile.getByRole('button', { name: '닫기' }).click();
      await expect(profile).toBeHidden();
      await page.unrouteAll();

      // 홈 왼쪽 구독 목록: 방송 중인 판매자에 LIVE, 누르면 그 방송으로
      await page.goto('/');
      const sidebar = page.getByRole('complementary', { name: '구독 중인 판매자' });
      const live = sidebar.getByRole('link', { name: /방송 보러 가기/ }).first();
      await expect(live).toBeVisible();
      await test.info().attach('홈 (구독 중인 판매자)', { body: await page.screenshot(), contentType: 'image/png' });
      await live.click();
      await expect(page).toHaveURL(new RegExp(`/lives/${first}$`));

      // 판매자가 방송을 끝내고 새 방송을 시작하면 시청자에게 알림 팝업이 뜬다.
      await page.goto('/');
      await expect(sidebar).toBeVisible();
      await endActive(request, sellerToken);
      const title = `E2E 알림 방송 ${Date.now()}`;
      const second = await startLive(request, sellerToken, title);
      const toast = page.getByRole('status').filter({ hasText: title });
      await expect(toast).toBeVisible({ timeout: 15_000 });
      await expect(toast).toContainText('방송을 시작했어요');
      await test.info().attach('방송 시작 알림 팝업', { body: await page.screenshot(), contentType: 'image/png' });

      // 알림함에도 남아 있고, 누르면 그 방송으로 간다.
      await page.getByRole('button', { name: /^알림(,|$)/ }).click();
      const item = page.getByRole('dialog', { name: '알림' }).getByRole('button').filter({ hasText: title });
      await expect(item).toBeVisible();
      await test.info().attach('알림함', { body: await page.screenshot(), contentType: 'image/png' });
      await item.click();
      await expect(page).toHaveURL(new RegExp(`/lives/${second}$`));
    } finally {
      await endActive(request, sellerToken);
    }
  });

  test('오른쪽 위 내 닉네임을 누르면 내 정보 창이 뜨고, 창에서 판매자 인증 화면으로 간다', async ({ page }) => {
    await login(page);
    const menu = page.getByRole('navigation', { name: '내 메뉴' });
    // 상단 탭: 홈이 라이브 왼쪽에 있고, 내 정보 탭은 없다.
    await expect(menu.getByRole('link').first()).toHaveText('홈');
    await expect(menu.getByRole('link', { name: '내 정보' })).toHaveCount(0);
    // 시청자 계정은 판매자 인증 전이라 [방송하기] 탭이 없고, 스튜디오 주소로 들어오면 인증 안내가 나온다.
    await expect(menu.getByRole('link', { name: '방송하기' })).toHaveCount(0);
    await page.goto('/studio');
    await expect(page.getByText('판매자 인증을 받아야 방송을 만들 수 있어요')).toBeVisible();
    await expect(page.getByRole('button', { name: '방송 만들기' })).toHaveCount(0);
    await test.info().attach('인증 전 스튜디오', { body: await page.screenshot(), contentType: 'image/png' });
    await page.goto('/');

    await page.getByRole('button', { name: /정보 보기$/ }).click();
    const dialog = page.getByRole('dialog', { name: '내 정보' });
    await expect(dialog.getByText('이메일')).toBeVisible();
    await test.info().attach('내 정보 창', { body: await page.screenshot(), contentType: 'image/png' });
    await dialog.getByRole('link', { name: '판매자 인증' }).click();
    await expect(dialog).toBeHidden();
    await expect(page).toHaveURL(/\/me\/seller$/);
    // 처음이면 신청 양식, 신청했으면 심사 상태가 나온다.
    await expect(page.getByLabel('상점 이름 (30자 이하)').or(page.getByText(/심사 중|승인|반려/).first())).toBeVisible();
  });
});

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(viewer.email);
  await page.getByLabel('비밀번호').fill(viewer.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

/** 시청 화면 판매자 이름 아래 "구독자 N명"의 N */
async function subscriberCount(page: Page) {
  const text = (await page.getByText(/구독자\s*[\d,]+\s*명/).first().textContent()) ?? '';
  return Number(text.match(/구독자\s*([\d,]+)/)?.[1]?.replace(/,/g, '') ?? NaN);
}

async function apiCall<T>(request: APIRequestContext, method: 'GET' | 'POST', path: string, token?: string, data?: unknown) {
  const response = await request.fetch(path, { method, data, headers: token ? { Authorization: `Bearer ${token}` } : undefined });
  const body = (await response.json().catch(() => ({}))) as Envelope<T>;
  return { ok: response.ok(), body };
}

async function apiLogin(request: APIRequestContext) {
  const login = await apiCall<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, seller);
  expect(login.ok, `판매자 로그인 실패: ${login.body.error?.message}`).toBe(true);
  return login.body.data?.accessToken ?? '';
}

/** 판매자의 종료되지 않은 방송(대기 중·방송 중)을 끝낸다. 판매자당 하나만 만들 수 있어서 새로 만들기 전에 정리한다. */
async function endActive(request: APIRequestContext, token: string) {
  const active = (await apiCall<{ id?: number }>(request, 'GET', '/api/v1/live-streams/me/active', token)).body.data;
  if (active?.id) await apiCall(request, 'POST', `/api/v1/live-streams/${active.id}/end`, token);
}

async function startLive(request: APIRequestContext, token: string, title: string) {
  const created = await apiCall<{ id: number }>(request, 'POST', '/api/v1/live-streams', token, { title });
  expect(created.ok, `방송 만들기 실패: ${created.body.error?.message}`).toBe(true);
  const id = created.body.data?.id ?? 0;
  const started = await apiCall(request, 'POST', `/api/v1/live-streams/${id}/start`, token);
  expect(started.ok, `방송 시작 실패: ${started.body.error?.message}`).toBe(true);
  return id;
}
