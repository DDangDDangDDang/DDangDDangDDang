/// <reference lib="dom" />
// 테스트 영상을 브라우저 안에서 만들기 위해 DOM 타입을 쓴다. (makeTestVideo)
import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

/*
 * 녹화 영상 송출
 * 판매자가 스튜디오에서 [녹화 영상]을 고르고 영상 파일을 고르면, 카메라 대신 그 영상을 반복 재생해 송출한다.
 * - 시청 화면 영상 위, 구독 목록(홈 왼쪽), 구독자의 방송 시작 알림에 "녹화 영상"이 표시된다.
 * - 방송 중에 카메라로 바꾸면 시청 화면의 표시도 바로 사라진다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 한다. 영상 파일은 브라우저에서 캔버스를 녹화해 만든다. (webm)
 * 계정: E2E_EMAIL / E2E_PASSWORD (시청자·구독자), E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (판매자)
 */

const viewer = {
  email: process.env.E2E_EMAIL ?? '',
  password: process.env.E2E_PASSWORD ?? '',
};
const seller = {
  email: process.env.E2E_SELLER_EMAIL ?? '',
  password: process.env.E2E_SELLER_PASSWORD ?? '',
};

type Envelope<T> = {
  success?: boolean;
  data?: T;
  error?: { code?: string; message?: string };
};

test.describe('녹화 영상 송출', () => {
  test.skip(!viewer.email || !seller.email, 'E2E_EMAIL, E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  test('녹화 영상을 반복 재생해 송출하면 시청 화면·구독 목록·방송 시작 알림에 녹화 영상으로 표시된다', async ({ browser, request }) => {
    test.setTimeout(150_000);
    // 판매자 방송은 API로 만들어 두고 스튜디오에서 시작한다. (API 로그인은 화면 로그인 전에 끝낸다)
    const sellerToken = await apiLogin(request, seller);
    await endActive(request, sellerToken);
    const title = `E2E 녹화 영상 ${Date.now()}`;
    const created = await apiCall<{ id: number; sellerId: number }>(request, 'POST', '/api/v1/live-streams', sellerToken, { title });
    expect(created.ok, `방송 만들기 실패: ${created.body.error?.message}`).toBe(true);
    const liveStreamId = created.body.data?.id ?? 0;
    const sellerId = created.body.data?.sellerId ?? 0;
    // 시청자는 판매자를 구독해 둔다. (방송 시작 알림·구독 목록)
    const viewerToken = await apiLogin(request, viewer);
    const subscribed = await apiCall(request, 'PUT', `/api/v1/sellers/${sellerId}/subscription`, viewerToken);
    expect(subscribed.ok, `구독 실패: ${subscribed.body.error?.message}`).toBe(true);

    const sellerContext = await browser.newContext();
    const viewerContext = await browser.newContext();
    const studio = await sellerContext.newPage();
    const home = await viewerContext.newPage();
    try {
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      const startButton = studio.getByRole('button', { name: '방송 시작' });
      await expect(startButton).toBeVisible();

      // 녹화 영상을 고르면 장치 선택 대신 파일 고르기가 나오고, 고른 영상을 미리보기에서 재생한다.
      await studio.getByRole('radio', { name: '녹화 영상' }).click();
      await expect(studio.getByText('송출할 영상 파일을 골라 주세요')).toBeVisible();
      const file = await makeTestVideo(studio);
      // 영상을 고르면 방송 정보에 녹화 영상으로 저장한다. (구독자 알림·시청 화면 표시에 쓰인다)
      const savedRecorded = studio.waitForResponse(
        (r) => r.url().endsWith(`/api/v1/live-streams/${liveStreamId}`) && r.request().method() === 'PATCH' && r.ok(),
      );
      await studio.getByLabel('영상 파일 고르기').setInputFiles({
        name: '상품 소개.webm',
        mimeType: 'video/webm',
        buffer: file,
      });
      await expect(studio.getByText('상품 소개.webm')).toBeVisible();
      const preview = studio.getByLabel('녹화 영상 미리보기');
      await expect.poll(() => videoWidth(preview), { timeout: 20_000 }).toBeGreaterThan(0);
      await expect(studio.getByText('녹화 영상', { exact: true }).first()).toBeVisible();
      await test.info().attach('스튜디오 (녹화 영상 고름)', {
        body: await studio.screenshot(),
        contentType: 'image/png',
      });

      // 시청자(구독자)는 홈에서 기다린다. 방송을 시작하면 "녹화 영상 방송을 시작했어요" 알림이 온다.
      await loginOnPage(home, viewer);
      await expect(startButton).toBeEnabled();
      expect((await (await savedRecorded).json()).data?.recorded).toBe(true);
      await startButton.click();
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({
        timeout: 20_000,
      });

      const toast = home.getByRole('status').filter({ hasText: title });
      await expect(toast).toBeVisible({ timeout: 15_000 });
      await expect(toast).toContainText('녹화 영상 방송을 시작했어요');
      await test.info().attach('방송 시작 알림 (녹화 영상)', {
        body: await home.screenshot(),
        contentType: 'image/png',
      });

      // 홈 왼쪽 구독 목록: 판매자 이름 아래에 녹화 영상 표시
      await home.reload();
      const sidebar = home.getByRole('complementary', {
        name: '구독 중인 판매자',
      });
      await expect(
        sidebar.getByRole('link', {
          name: new RegExp(`녹화 영상 방송 보러 가기: ${title}`),
        }),
      ).toBeVisible();
      await expect(sidebar.getByText('녹화 영상', { exact: true })).toBeVisible();
      await test.info().attach('홈 구독 목록 (녹화 영상)', {
        body: await home.screenshot(),
        contentType: 'image/png',
      });

      // 시청 화면: 영상이 나오고 LIVE 옆에 녹화 영상 표시. 영상이 짧아도 반복 재생되어 계속 이어진다.
      await home.goto(`/lives/${liveStreamId}`);
      const video = home.locator('video');
      await expect.poll(() => videoWidth(video), { timeout: 30_000 }).toBeGreaterThan(0);
      const stage = home.locator('div.aspect-video').first();
      await expect(stage.getByText('녹화 영상', { exact: true })).toBeVisible();
      const t1 = await videoTime(video);
      // 테스트 영상은 2초짜리다. 그보다 오래 지나도 영상이 멈추지 않는다.
      await home.waitForTimeout(4_000);
      expect(await videoTime(video)).toBeGreaterThan(t1 + 2);
      await test.info().attach('시청 화면 (녹화 영상)', {
        body: await home.screenshot(),
        contentType: 'image/png',
      });

      // 판매자가 카메라로 바꾸면 시청 화면의 녹화 영상 표시가 바로 사라진다.
      await studio.getByRole('radio', { name: '카메라' }).click();
      await expect(studio.getByLabel('내 카메라 미리보기')).toBeVisible({
        timeout: 20_000,
      });
      await expect(stage.getByText('녹화 영상', { exact: true })).toBeHidden({
        timeout: 15_000,
      });
      await expect.poll(() => videoWidth(video), { timeout: 20_000 }).toBeGreaterThan(0);
      await test.info().attach('시청 화면 (카메라로 바꿈)', {
        body: await home.screenshot(),
        contentType: 'image/png',
      });
    } finally {
      const token = await apiLogin(request, seller).catch(() => '');
      if (token) await endActive(request, token);
      await sellerContext.close();
      await viewerContext.close();
    }
  });
});

/** 2초짜리 테스트 영상(webm). 캔버스에 움직이는 그림을 그려 MediaRecorder로 녹화한다. */
async function makeTestVideo(page: Page): Promise<Buffer> {
  const base64 = await page.evaluate(async () => {
    const canvas = document.createElement('canvas');
    canvas.width = 640;
    canvas.height = 360;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('canvas');
    const stream = canvas.captureStream(30);
    const recorder = new MediaRecorder(stream, { mimeType: 'video/webm' });
    const chunks: Blob[] = [];
    recorder.ondataavailable = (event) => chunks.push(event.data);
    const done = new Promise((resolve) => (recorder.onstop = resolve));
    recorder.start();
    const startedAt = performance.now();
    await new Promise<void>((resolve) => {
      const draw = () => {
        const elapsed = performance.now() - startedAt;
        context.fillStyle = '#1d4ed8';
        context.fillRect(0, 0, 640, 360);
        context.fillStyle = '#ffffff';
        context.fillRect((elapsed / 5) % 640, 140, 80, 80);
        context.font = '32px sans-serif';
        context.fillText('녹화 영상 테스트', 200, 80);
        if (elapsed < 2000) requestAnimationFrame(draw);
        else resolve();
      };
      draw();
    });
    recorder.stop();
    await done;
    const bytes = new Uint8Array(await new Blob(chunks, { type: 'video/webm' }).arrayBuffer());
    let binary = '';
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary);
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

async function videoWidth(locator: Locator) {
  return locator
    .first()
    .evaluate((element) => (element as unknown as { videoWidth: number }).videoWidth)
    .catch(() => 0);
}

async function videoTime(locator: Locator) {
  return locator
    .first()
    .evaluate((element) => (element as unknown as { currentTime: number }).currentTime)
    .catch(() => 0);
}

async function apiCall<T>(request: APIRequestContext, method: 'GET' | 'POST' | 'PUT', path: string, token?: string, data?: unknown) {
  const response = await request.fetch(path, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  });
  const body = (await response.json().catch(() => ({}))) as Envelope<T>;
  return { ok: response.ok(), body };
}

async function apiLogin(request: APIRequestContext, account: { email: string; password: string }) {
  const login = await apiCall<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, account);
  expect(login.ok, `로그인 실패: ${login.body.error?.message}`).toBe(true);
  return login.body.data?.accessToken ?? '';
}

/** 판매자의 종료되지 않은 방송(대기 중·방송 중)을 끝낸다. 판매자당 하나만 만들 수 있어서 새로 만들기 전에 정리한다. */
async function endActive(request: APIRequestContext, token: string) {
  const active = (await apiCall<{ id?: number }>(request, 'GET', '/api/v1/live-streams/me/active', token)).body.data;
  if (active?.id) await apiCall(request, 'POST', `/api/v1/live-streams/${active.id}/end`, token);
}
