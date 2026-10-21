import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

/*
 * 1. 로그인 → 홈 → 방송 시청 → 채팅 전송
 * 2. 판매자 스튜디오에서 가짜 카메라로 송출 → 시청 화면에 영상이 나온다 (playwright.config.ts의 가짜 장치 설정)
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit 포함)가 떠 있어야 한다. 계정은 환경변수로 받는다. (frontend/README.md "E2E 테스트")
 * - E2E_EMAIL / E2E_PASSWORD: 시청자 계정 (화면에서 로그인)
 * - E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD: 방송을 여는 계정 (1은 API로, 2는 스튜디오 화면으로 방송을 만들고 시작).
 *   없으면 1은 시청자 계정으로 열고, 2는 건너뛴다.
 *
 * 판매자 계정에 종료되지 않은 방송이 있으면 그 방송을 쓰고, 이 테스트가 시작한 방송만 끝에 종료한다.
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = {
  email: process.env.E2E_SELLER_EMAIL || viewer.email,
  password: process.env.E2E_SELLER_PASSWORD || viewer.password,
};

type Envelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };
type LiveStream = { id: number; title: string; status: 'SCHEDULED' | 'LIVE' | 'ENDED' };

async function apiCall<T>(request: APIRequestContext, method: 'GET' | 'POST', path: string, token?: string, data?: unknown) {
  const response = await request.fetch(path, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  const body = (await response.json().catch(() => ({}))) as Envelope<T>;
  return { ok: response.ok(), body };
}

/** 판매자 계정으로 방송 중인 방송을 하나 준비한다. */
async function prepareLiveStream(request: APIRequestContext) {
  const login = await apiCall<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, seller);
  expect(login.ok, `판매자 로그인 실패: ${login.body.error?.message}`).toBe(true);
  const token = login.body.data?.accessToken ?? '';

  let stream: LiveStream | undefined;
  let createdByTest = false;
  const created = await apiCall<LiveStream>(request, 'POST', '/api/v1/live-streams', token, {
    title: `E2E 방송 ${new Date().toISOString().slice(0, 19)}`,
    description: 'Playwright E2E 테스트가 만든 방송입니다.',
  });
  if (created.ok) {
    stream = created.body.data;
    createdByTest = true;
  } else if (created.body.error?.code === 'LIVESTREAM_010') {
    // 이미 종료되지 않은 방송이 있으면 그 방송을 쓴다.
    stream = (await apiCall<LiveStream>(request, 'GET', '/api/v1/live-streams/me/active', token)).body.data;
  }
  expect(stream, `방송 만들기 실패: ${created.body.error?.message}`).toBeTruthy();
  if (!stream) throw new Error('방송을 준비하지 못했습니다.');

  let startedByTest = false;
  if (stream.status === 'SCHEDULED') {
    const started = await apiCall(request, 'POST', `/api/v1/live-streams/${stream.id}/start`, token);
    expect(started.ok, `방송 시작 실패: ${started.body.error?.message}`).toBe(true);
    startedByTest = true;
  }
  return { stream, token, endAfter: createdByTest || startedByTest };
}

test.describe('방송 시청', () => {
  test.skip(!viewer.email || !viewer.password, 'E2E_EMAIL, E2E_PASSWORD 환경변수가 필요합니다.');

  let prepared: Awaited<ReturnType<typeof prepareLiveStream>> | undefined;

  test.beforeAll(async ({ request }) => {
    prepared = await prepareLiveStream(request);
  });

  test.afterAll(async ({ request }) => {
    if (prepared?.endAfter) {
      await apiCall(request, 'POST', `/api/v1/live-streams/${prepared.stream.id}/end`, prepared.token);
    }
  });

  test('로그인하고 홈에서 방송을 찾아 들어가 채팅을 보낸다', async ({ page }) => {
    if (!prepared) throw new Error('방송이 준비되지 않았습니다.');
    const { stream } = prepared;

    await loginOnPage(page, viewer);

    // 홈: 방송 섹션이 보이고, 전체 보기 → 검색으로 방금 연 방송을 찾는다.
    await page.goto('/');
    await expect(page.getByRole('heading', { name: '지금 인기 라이브' })).toBeVisible();
    await page.getByRole('region', { name: '지금 인기 라이브' }).getByRole('link', { name: '전체 보기' }).click();
    await expect(page).toHaveURL(/\/lives/);
    await page.getByRole('searchbox', { name: '방송 검색' }).fill(stream.title);
    await page.getByRole('button', { name: '검색' }).click();
    await page.getByRole('link', { name: new RegExp(escapeRegExp(stream.title)) }).click();

    // 방송 시청
    await expect(page).toHaveURL(new RegExp(`/lives/${stream.id}$`));
    await expect(page.getByRole('heading', { level: 1, name: stream.title })).toBeVisible();
    await expect(page.getByText('LIVE', { exact: true }).first()).toBeVisible();

    // 채팅 전송 → 내 메시지가 채팅 목록에 나온다.
    const chatInput = page.getByLabel('채팅 입력');
    await expect(chatInput).toBeEnabled({ timeout: 20_000 });
    const content = `E2E 채팅 ${Date.now()}`;
    await chatInput.fill(content);
    await page.getByRole('button', { name: '채팅 전송' }).click();
    await expect(page.getByRole('list', { name: '채팅 메시지' }).getByText(content)).toBeVisible();
    await expect(chatInput).toHaveValue('');
  });
});

test.describe('판매자 송출', () => {
  test.skip(!viewer.email || !viewer.password, 'E2E_EMAIL, E2E_PASSWORD 환경변수가 필요합니다.');
  test.skip(seller.email === viewer.email, '판매자와 시청자가 다른 계정이어야 합니다. (E2E_SELLER_EMAIL, E2E_SELLER_PASSWORD)');

  test('스튜디오에서 가짜 카메라로 방송을 시작하면 시청 화면에 영상이 나온다', async ({ browser }) => {
    test.setTimeout(120_000);
    const sellerContext = await browser.newContext();
    const viewerContext = await browser.newContext();
    const studio = await sellerContext.newPage();
    const watch = await viewerContext.newPage();
    let endAfter = false;

    try {
      // 판매자: 스튜디오. 종료되지 않은 방송이 있으면 그 방송을 쓰고, 없으면 만든다.
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      const createButton = studio.getByRole('button', { name: '방송 만들기' });
      const startButton = studio.getByRole('button', { name: '방송 시작' });
      const endButton = studio.getByRole('button', { name: '방송 종료' });
      await expect(createButton.or(startButton).or(endButton)).toBeVisible();
      if (await createButton.isVisible()) {
        await studio.getByLabel('방송 제목 (100자 이하)').fill(`E2E 송출 ${new Date().toISOString().slice(0, 19)}`);
        await createButton.click();
        endAfter = true;
      }

      // 방송 전 미리보기: 가짜 카메라 영상이 나온다.
      const preview = studio.getByLabel('내 카메라 미리보기');
      await expect.poll(() => videoWidth(preview), { timeout: 20_000 }).toBeGreaterThan(0);

      if (await startButton.isVisible()) {
        await expect(startButton).toBeEnabled();
        await startButton.click();
        endAfter = true;
      }
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });
      const watchHref = await studio.getByRole('link', { name: '시청 화면 열기' }).getAttribute('href');
      expect(watchHref).toMatch(/^\/lives\/\d+$/);

      // 시청자: 같은 방송에서 판매자 영상이 재생된다.
      await loginOnPage(watch, viewer);
      await watch.goto(watchHref ?? '/');
      const video = watch.locator('video');
      await expect.poll(() => videoWidth(video), { timeout: 30_000 }).toBeGreaterThan(0);
      await expect(watch.getByText('판매자 화면을 기다리고 있어요')).toBeHidden();

      // 판매자 화면에 시청자 수가 반영된다.
      await expect(studio.getByText(/시청\s*1\s*명/)).toBeVisible({ timeout: 20_000 });

      // 판매자가 새로고침해도 송출 토큰을 다시 받아(/publisher-token) 이어서 송출한다.
      await studio.reload();
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });
      await expect.poll(() => videoWidth(video), { timeout: 30_000 }).toBeGreaterThan(0);
      // 송출 중인 두 화면을 보고서(playwright-report)에 남긴다.
      await test.info().attach('판매자 스튜디오 (송출 중)', { body: await studio.screenshot(), contentType: 'image/png' });
      await test.info().attach('시청 화면 (판매자 영상)', { body: await watch.screenshot(), contentType: 'image/png' });

      // 시청자가 보낸 채팅이 판매자 스튜디오에 나온다.
      const content = `E2E 스튜디오 채팅 ${Date.now()}`;
      const chatInput = watch.getByLabel('채팅 입력');
      await expect(chatInput).toBeEnabled({ timeout: 20_000 });
      await chatInput.fill(content);
      await watch.getByRole('button', { name: '채팅 전송' }).click();
      await expect(studio.getByRole('list', { name: '채팅 메시지' }).getByText(content)).toBeVisible();

      if (endAfter) {
        // 방송 종료: 확인 창에서 종료하면 시청 화면에도 종료가 나온다.
        await endButton.click();
        await studio.getByRole('dialog').getByRole('button', { name: '방송 종료' }).click();
        await expect(studio.getByText(/방송을 종료했어요/)).toBeVisible();
        endAfter = false;
        await expect(watch.getByText('방송이 끝났어요')).toBeVisible({ timeout: 20_000 });
      }
    } finally {
      if (endAfter) {
        // 테스트가 중간에 실패해도 이 테스트가 시작한 방송은 정리한다.
        await endActiveStream(studio.request);
      }
      await sellerContext.close();
      await viewerContext.close();
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

/** 영상 요소의 실제 영상 폭. 영상이 아직 나오지 않으면 0 */
async function videoWidth(locator: Locator) {
  return locator.first().evaluate((element) => (element as unknown as { videoWidth: number }).videoWidth).catch(() => 0);
}

async function endActiveStream(request: APIRequestContext) {
  const login = await apiCall<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, seller);
  const token = login.body.data?.accessToken;
  if (!token) return;
  const active = (await apiCall<LiveStream>(request, 'GET', '/api/v1/live-streams/me/active', token)).body.data;
  if (active?.status === 'LIVE') await apiCall(request, 'POST', `/api/v1/live-streams/${active.id}/end`, token);
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
