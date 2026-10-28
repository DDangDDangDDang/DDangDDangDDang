import { expect, test, type Locator, type Page } from '@playwright/test';

/*
 * 홈 방송 미리보기 (치지직처럼)
 * 판매자 스튜디오에서 가짜 카메라로 방송을 시작한 뒤, 로그인하지 않은 손님이 홈에서
 * - 방송 카드를 스쳐 지나가면 연결하지 않고, 잠시 올려 두면 썸네일 자리에서 소리 없이 영상이 나온다. 떠나면 썸네일로 돌아온다.
 * - 미리보기는 시청 입장(/join)이 아니라 미리보기 토큰(/preview)으로 접속해, 판매자 스튜디오의 시청자 수가 늘지 않는다.
 * - 카드를 누르면 시청 화면으로 간다.
 * - 이 방송이 대표 라이브면 마우스를 올리지 않아도 자동으로 미리보기가 나오고 [소리 켜기]가 보인다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 한다. 판매자 계정: E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (frontend/README.md "E2E 테스트")
 * 판매자 계정에 종료되지 않은 방송이 있으면 그 방송을 쓰고, 이 테스트가 시작한 방송만 끝에 종료한다.
 */

const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

test.describe('홈 방송 미리보기', () => {
  test.skip(!seller.email || !seller.password, 'E2E_SELLER_EMAIL, E2E_SELLER_PASSWORD 환경변수가 필요합니다.');

  test('방송 카드에 마우스를 올려 두면 시청자 수를 늘리지 않고 소리 없는 미리보기가 나온다', async ({ browser }) => {
    test.setTimeout(120_000);
    const sellerContext = await browser.newContext();
    // 동작 줄이기: 대표 라이브 자동 미리보기를 꺼서, 카드 마우스 올리기로 생기는 요청만 센다.
    const guestContext = await browser.newContext({ reducedMotion: 'reduce' });
    const studio = await sellerContext.newPage();
    const guest = await guestContext.newPage();
    let endAfter = false;
    let liveStreamId: string | undefined;

    try {
      // 판매자: 스튜디오에서 방송을 만들고(없으면) 가짜 카메라로 시작한다.
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      const createButton = studio.getByRole('button', { name: '방송 만들기' });
      const startButton = studio.getByRole('button', { name: '방송 시작' });
      const endButton = studio.getByRole('button', { name: '방송 종료' });
      await expect(createButton.or(startButton).or(endButton)).toBeVisible();
      if (await createButton.isVisible()) {
        await studio.getByLabel('방송 제목 (100자 이하)').fill(`E2E 미리보기 ${new Date().toISOString().slice(0, 19)}`);
        await createButton.click();
        endAfter = true;
      }
      await expect.poll(() => videoWidth(studio.getByLabel('내 카메라 미리보기')), { timeout: 20_000 }).toBeGreaterThan(0);
      if (await startButton.isVisible()) {
        await expect(startButton).toBeEnabled();
        await startButton.click();
        endAfter = true;
      }
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });
      const watchHref = (await studio.getByRole('link', { name: '시청 화면 열기' }).getAttribute('href')) ?? '';
      expect(watchHref).toMatch(/^\/lives\/\d+$/);
      liveStreamId = watchHref.split('/').pop();
      const viewerCount = studio.getByText(/^시청\s*[\d,]+\s*명$/).first();
      await expect(viewerCount).toHaveText(/시청\s*0\s*명/);

      // 손님: 홈에서 이 방송 카드를 찾는다.
      const previewRequests: string[] = [];
      const joinRequests: string[] = [];
      guest.on('request', (request) => {
        if (request.url().endsWith(`/api/v1/live-streams/${liveStreamId}/preview`)) previewRequests.push(request.url());
        if (request.url().endsWith(`/api/v1/live-streams/${liveStreamId}/join`)) joinRequests.push(request.url());
      });
      await guest.goto('/');
      const card = guest.getByRole('region', { name: '지금 인기 라이브' }).locator(`a[href="${watchHref}"]`);
      await expect(card).toBeVisible();
      const cardPreview = card.getByTestId('live-preview');

      // 스쳐 지나가면 연결하지 않는다.
      await card.hover();
      await guest.mouse.move(0, 0);
      await guest.waitForTimeout(800);
      expect(previewRequests, '스쳐 지나간 카드는 미리보기를 연결하지 않는다').toHaveLength(0);
      await expect(cardPreview).toHaveCount(0);

      // 잠시 올려 두면 썸네일 자리에서 영상이 나온다. (영상만, 음소거)
      await card.hover();
      await expect(cardPreview).toHaveAttribute('data-playing', 'true', { timeout: 20_000 });
      await expect.poll(() => videoWidth(cardPreview.locator('video')), { timeout: 10_000 }).toBeGreaterThan(0);
      expect(await cardPreview.locator('video').evaluate((element) => (element as unknown as { muted: boolean }).muted)).toBe(true);
      await expect(card.locator('audio')).toHaveCount(0);
      expect(previewRequests).toHaveLength(1);
      expect(joinRequests, '미리보기는 시청 입장 토큰을 쓰지 않는다').toHaveLength(0);
      await test.info().attach('홈 방송 카드 미리보기', { body: await guest.screenshot(), contentType: 'image/png' });

      // 미리보기는 숨은 참가자라 판매자 스튜디오의 시청자 수가 그대로다.
      await guest.waitForTimeout(2_000);
      await expect(viewerCount).toHaveText(/시청\s*0\s*명/);

      // 떠나면 바로 끊고 썸네일로 돌아온다.
      await guest.mouse.move(0, 0);
      await expect(cardPreview).toHaveCount(0);

      // 이 방송이 대표 라이브면, 동작 줄이기를 켜지 않은 사람에게는 자동으로 미리보기가 나오고 [소리 켜기]가 보인다.
      const autoContext = await browser.newContext();
      try {
        const home = await autoContext.newPage();
        await home.goto('/');
        const featured = home.getByRole('region', { name: '대표 라이브' });
        await expect(featured).toBeVisible();
        if ((await featured.getByRole('link').first().getAttribute('href')) === watchHref) {
          await expect(featured.getByTestId('live-preview')).toHaveAttribute('data-playing', 'true', { timeout: 20_000 });
          await expect(featured.getByRole('button', { name: '소리 켜기' })).toBeVisible();
          await test.info().attach('홈 대표 라이브 자동 미리보기', { body: await home.screenshot(), contentType: 'image/png' });
          await expect(viewerCount).toHaveText(/시청\s*0\s*명/);
        }
      } finally {
        await autoContext.close();
      }

      // 누르면 시청 화면으로 간다. (여기서부터는 실제 시청이라 시청자 수가 는다)
      await card.click();
      await expect(guest).toHaveURL(new RegExp(`${watchHref}$`));
      await expect(viewerCount).toHaveText(/시청\s*1\s*명/, { timeout: 20_000 });
    } finally {
      if (endAfter && (await studio.getByRole('button', { name: '방송 종료' }).isVisible())) {
        await studio.getByRole('button', { name: '방송 종료' }).click();
        await studio.getByRole('dialog').getByRole('button', { name: '방송 종료' }).click();
        await expect(studio.getByText(/방송을 종료했어요/)).toBeVisible();
      }
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

/** 영상 요소의 실제 영상 폭. 영상이 아직 나오지 않으면 0 */
async function videoWidth(locator: Locator) {
  return locator
    .first()
    .evaluate((element) => (element as unknown as { videoWidth: number }).videoWidth)
    .catch(() => 0);
}
