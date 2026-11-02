import { expect, test, type Locator, type Page } from '@playwright/test';

/*
 * 판매자가 직접 올리는 방송 썸네일
 * - 방송을 만들 때 썸네일을 고르면, 방송 예정 카드에 그 이미지가 보인다.
 * - 방송을 시작해 송출 화면 자동 캡처가 올라가도 직접 올린 썸네일이 카드에 그대로 남는다. (자동 캡처가 덮어쓰지 않음)
 * - 지우면 카드가 송출 화면 자동 캡처로 돌아간다.
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit)가 떠 있어야 한다. 판매자 계정: E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (frontend/README.md "E2E 테스트")
 * 판매자 계정에 종료되지 않은 방송이 없어야 한다. (방송을 새로 만들어 쓰고, 끝에 종료한다)
 */

const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

test.describe('직접 올리는 방송 썸네일', () => {
  test.skip(!seller.email || !seller.password, 'E2E_SELLER_EMAIL, E2E_SELLER_PASSWORD 환경변수가 필요합니다.');

  test('만들 때 올린 썸네일이 카드에 보이고, 자동 캡처가 덮지 않으며, 지우면 자동 캡처로 돌아간다', async ({ browser }) => {
    test.setTimeout(180_000);
    const sellerContext = await browser.newContext();
    const guestContext = await browser.newContext({ reducedMotion: 'reduce' });
    const studio = await sellerContext.newPage();
    const guest = await guestContext.newPage();
    const title = `E2E 썸네일 ${new Date().toISOString().slice(0, 19)}`;
    let created = false;

    try {
      await loginOnPage(studio, seller);
      await studio.goto('/studio');
      const createButton = studio.getByRole('button', { name: '방송 만들기' });
      await expect(createButton.or(studio.getByRole('button', { name: /방송 (시작|종료)/ }))).toBeVisible();
      // 앞서 실패한 실행이 남긴 방송이 있으면 끝내고 새로 만든다.
      if (!(await createButton.isVisible())) await endActiveStream(studio);
      await expect(createButton).toBeVisible();

      // 방송 만들기: 제목과 함께 16:9 PNG를 고른다. 미리보기가 나오고, 방송을 만든 뒤 올라간다.
      await studio.getByLabel('방송 제목 (100자 이하)').fill(title);
      // 방송 예정 목록은 시작 예정 시간을 정한 방송만 보여 준다.
      await studio.getByLabel('시작 예정 시간 (선택)').fill(dateTimeInput(new Date(Date.now() + 60 * 60 * 1000)));
      await studio.getByLabel('썸네일 이미지 파일').setInputFiles({ name: 'thumbnail.png', mimeType: 'image/png', buffer: await makePng(studio) });
      await expect(studio.getByRole('group', { name: '방송 썸네일 (선택)' }).locator('img')).toHaveAttribute('src', /^blob:/);
      await test.info().attach('방송 만들기 썸네일 미리보기', { body: await studio.screenshot(), contentType: 'image/png' });
      const confirmed = studio.waitForResponse((r) => r.url().endsWith('/custom-thumbnail') && r.request().method() === 'PUT');
      await createButton.click();
      expect((await confirmed).status()).toBe(200);
      created = true;
      await expect(studio.getByRole('button', { name: '방송 시작' })).toBeVisible();

      // 방송 예정 목록 카드에 직접 올린 썸네일이 보인다.
      await guest.goto('/lives?status=SCHEDULED');
      const scheduledCard = guest.getByRole('link').filter({ hasText: title });
      await expectLoadedImage(scheduledCard.locator('img[src*="/custom/"]'));
      await test.info().attach('방송 예정 카드 (직접 올린 썸네일)', { body: await guest.screenshot(), contentType: 'image/png' });

      // 방송 시작: 송출 화면 자동 캡처가 올라가도 카드는 직접 올린 썸네일 그대로다.
      await expect.poll(() => videoWidth(studio.getByLabel('내 카메라 미리보기')), { timeout: 20_000 }).toBeGreaterThan(0);
      const autoCapture = studio.waitForResponse((r) => /\/live-streams\/\d+\/thumbnail$/.test(r.url()) && r.request().method() === 'PUT', {
        timeout: 40_000,
      });
      await studio.getByRole('button', { name: '방송 시작' }).click();
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });
      expect((await autoCapture).status(), '송출 화면 자동 캡처가 올라갔다').toBe(204);
      const watchHref = ((await studio.getByRole('link', { name: '시청 화면 열기' }).getAttribute('href')) ?? '').split('?')[0];
      const liveStreamId = watchHref.split('/').pop();

      await guest.goto('/lives');
      const liveCard = guest.locator(`a[href="${watchHref}"]`).first();
      await expectLoadedImage(liveCard.locator('img[src*="/custom/"]'));
      await test.info().attach('방송 중 카드 (자동 캡처가 있어도 직접 올린 썸네일)', { body: await guest.screenshot(), contentType: 'image/png' });

      // 지우기: 카드가 송출 화면 자동 캡처로 돌아간다.
      await studio.getByRole('button', { name: '정보 수정' }).click();
      const editor = studio.getByLabel('방송 정보 수정');
      await editor.getByRole('button', { name: '지우기' }).click();
      await expect(studio.getByText(/자동 캡처로 돌아가요/)).toBeVisible();
      await expect(editor.getByText('자동 캡처 사용 중')).toBeVisible();
      await expect(editor.getByRole('group', { name: '방송 썸네일 (선택)' }).locator('img')).toHaveCount(0);
      await test.info().attach('스튜디오 썸네일 지운 뒤', { body: await studio.screenshot(), contentType: 'image/png' });

      await guest.reload();
      const autoImage = liveCard.locator(`img[src*="live-thumbnails/${liveStreamId}/"]`);
      await expectLoadedImage(autoImage);
      await expect(autoImage).not.toHaveAttribute('src', /\/custom\//);
      await test.info().attach('지운 뒤 카드 (자동 캡처)', { body: await guest.screenshot(), contentType: 'image/png' });
    } finally {
      if (created) await endActiveStream(studio);
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

/** 종료되지 않은 방송을 끝낸다. 방송 예정이면 종료 버튼이 없어 시작한 뒤 끝낸다. */
async function endActiveStream(studio: Page) {
  const startButton = studio.getByRole('button', { name: '방송 시작' });
  const endButton = studio.getByRole('button', { name: '방송 종료' });
  if (await startButton.isVisible()) {
    await expect(startButton).toBeEnabled({ timeout: 20_000 });
    await startButton.click();
  }
  await expect(endButton).toBeVisible({ timeout: 20_000 });
  await endButton.click();
  await studio.getByRole('dialog').getByRole('button', { name: '방송 종료' }).click();
  await expect(studio.getByText(/방송을 종료했어요/)).toBeVisible();
}

/** datetime-local 입력 값 (지역 시각, 분까지) */
function dateTimeInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** 브라우저 캔버스로 1280×720 PNG를 만든다. (노란 바탕에 글자) */
async function makePng(page: Page): Promise<Buffer> {
  const base64 = await page.evaluate(() => {
    const canvas = document.createElement('canvas');
    canvas.width = 1280;
    canvas.height = 720;
    const context = canvas.getContext('2d');
    if (context) {
      context.fillStyle = '#f5c84c';
      context.fillRect(0, 0, 1280, 720);
      context.fillStyle = '#111111';
      context.font = 'bold 96px sans-serif';
      context.fillText('E2E 썸네일', 120, 400);
    }
    return canvas.toDataURL('image/png').split(',')[1];
  });
  return Buffer.from(base64, 'base64');
}

/** 이미지가 보이고 실제로 불러왔다. (주소만 있고 깨진 이미지가 아니다) */
async function expectLoadedImage(image: Locator) {
  await expect(image.first()).toBeVisible();
  await expect.poll(() => image.first().evaluate((element) => (element as unknown as { naturalWidth: number }).naturalWidth), { timeout: 10_000 }).toBeGreaterThan(0);
}

/** 영상 요소의 실제 영상 폭. 영상이 아직 나오지 않으면 0 */
async function videoWidth(locator: Locator) {
  return locator
    .first()
    .evaluate((element) => (element as unknown as { videoWidth: number }).videoWidth)
    .catch(() => 0);
}
