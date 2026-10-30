import { expect, test, type APIRequestContext, type Locator, type Page } from '@playwright/test';

/*
 * 1. 로그인 → 홈 → 방송 시청 → 채팅 전송
 *    로그인하지 않은 손님: 홈·방송 시청·채팅 보기 → 보내려 하면 "로그인이 필요합니다" 창 → 창에서 로그인 → 쓰던 채팅 그대로 보내기
 * 2. 판매자 스튜디오에서 가짜 카메라로 송출 → 시청 화면에 영상이 나온다 (playwright.config.ts의 가짜 장치 설정)
 *    방송 중 다른 화면으로 가도 송출이 이어지고(작은 방송 창), 스튜디오 안에서 상품을 등록해 바로 경매를 시작한다.
 *    시작한 경매와 미리 만들어 둔 경매(방송에 올리기)가 시청 화면의 경매 목록과 채팅 안내에 바로 나온다.
 *    방송 예정 화면에서 기다리던 시청자는 판매자가 시작하면 새로고침 없이 영상을 보고, 채팅에 시작 안내가 올라온다.
 *    방송을 만들 때 넣은 해시태그가 시청 화면과 방송 목록 카드에 나온다.
 *    판매자가 영상 위에 띄운 경매가 시청 화면 영상 위에 나오고, 내리면 사라진다.
 *    판매자가 올린 채팅 공지가 시청 화면 채팅창 위에 고정되고, 닫아도 다시 들어오면 보인다. 경매 목록의 [입찰하기]는 입찰 창을 연다.
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
    await page.getByRole('main').getByRole('button', { name: '검색' }).click();
    // 왼쪽 "구독 중인 판매자"의 LIVE 링크 이름(aria-label)에도 방송 제목이 들어가므로, 제목 글자가 화면에 보이는 방송 카드만 고른다.
    await page.getByRole('link').filter({ hasText: stream.title }).click();

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

    // 시청 중에 다른 화면(헤더 [경매])으로 가면 이 탭에서 옮겨 가고, 방송은 오른쪽 아래 작은 플레이어에서 이어진다.
    await page.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '경매', exact: true }).click();
    await expect(page).toHaveURL(/\/auctions$/);
    const mini = page.getByRole('complementary', { name: '보던 방송' });
    await expect(mini).toBeVisible();
    await expect(mini).toContainText(stream.title);
    // [방송으로 돌아가기]로 시청 화면에 돌아오면 작은 플레이어는 사라진다.
    await mini.getByRole('link', { name: '방송으로 돌아가기' }).click();
    await expect(page).toHaveURL(new RegExp(`/lives/${stream.id}$`));
    await expect(page.getByRole('heading', { level: 1, name: stream.title })).toBeVisible();
    await expect(mini).toBeHidden();
    // 다시 떠난 뒤 [닫기]를 누르면 작은 플레이어가 닫힌다.
    await page.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '홈' }).click();
    await expect(mini).toBeVisible();
    await mini.getByRole('button', { name: '작은 플레이어 닫기' }).click();
    await expect(mini).toBeHidden();
  });

  test('판매자가 자기 방송의 시청 화면을 떠나면 작은 플레이어를 띄우지 않는다', async ({ page }) => {
    test.skip(seller.email === viewer.email, 'E2E_SELLER_EMAIL, E2E_SELLER_PASSWORD 환경변수가 필요합니다.');
    if (!prepared) throw new Error('방송이 준비되지 않았습니다.');
    const { stream } = prepared;

    await loginOnPage(page, seller);
    await page.goto(`/lives/${stream.id}`);
    await expect(page.getByRole('heading', { level: 1, name: stream.title })).toBeVisible();
    await expect(page.getByText('LIVE', { exact: true }).first()).toBeVisible();

    // 화면 안에서 다른 화면으로 옮겨 가도(새로 고침 없이) 내 방송은 작은 플레이어로 이어지지 않는다.
    await page.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '경매', exact: true }).click();
    await expect(page).toHaveURL(/\/auctions$/);
    await page.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '홈' }).click();
    await expect(page.getByRole('heading', { name: '지금 인기 라이브' })).toBeVisible();
    await expect(page.getByRole('complementary', { name: '보던 방송' })).toHaveCount(0);
  });

  test('로그인하지 않아도 방송과 채팅을 보고, 채팅을 보내려 하면 로그인 창에서 로그인한 뒤 이어서 보낸다', async ({ browser }) => {
    if (!prepared) throw new Error('방송이 준비되지 않았습니다.');
    const { stream } = prepared;
    const guestContext = await browser.newContext();
    const memberContext = await browser.newContext();
    const guest = await guestContext.newPage();
    const member = await memberContext.newPage();

    try {
      // 손님: 홈에 대표 라이브와 방송 섹션이 보이고, 로그인은 헤더 오른쪽 위에서 한다.
      await guest.goto('/');
      await expect(guest.getByRole('region', { name: '대표 라이브' })).toBeVisible();
      await expect(guest.getByRole('banner').getByRole('link', { name: '로그인' })).toBeVisible();
      await expect(guest.getByRole('heading', { name: '지금 인기 라이브' })).toBeVisible();

      // 손님: 방송 시청 화면에 들어가 영상에 입장하고(손님 토큰) 채팅을 본다.
      await guest.goto(`/lives/${stream.id}`);
      await expect(guest.getByRole('heading', { level: 1, name: stream.title })).toBeVisible();
      await expect(guest.getByText('판매자 화면을 기다리고 있어요').or(guest.locator('video'))).toBeVisible({ timeout: 20_000 });
      await expect(guest.getByText('방송에 연결하지 못했어요')).toBeHidden();
      // 손님 채팅 연결(구독)이 끝난 뒤에 회원 채팅을 보낸다. (입력란은 연결돼야 켜짐, 채팅 이력은 없음)
      await expect(guest.getByLabel('채팅 입력')).toBeEnabled({ timeout: 20_000 });

      // 다른 회원이 보낸 채팅이 손님 화면에 나온다.
      await loginOnPage(member, seller);
      await member.goto(`/lives/${stream.id}`);
      const memberInput = member.getByLabel('채팅 입력');
      await expect(memberInput).toBeEnabled({ timeout: 20_000 });
      const hello = `E2E 회원 채팅 ${Date.now()}`;
      await memberInput.fill(hello);
      await member.getByRole('button', { name: '채팅 전송' }).click();
      await expect(guest.getByRole('list', { name: '채팅 메시지' }).getByText(hello)).toBeVisible();

      // 손님이 보내려고 하면 로그인 창이 뜨고, 창에서 로그인하면 같은 화면에서 쓰던 채팅을 이어서 보낸다.
      const guestInput = guest.getByLabel('채팅 입력');
      const draft = `E2E 손님이 쓴 채팅 ${Date.now()}`;
      await guestInput.fill(draft);
      await guest.getByRole('button', { name: '채팅 전송' }).click();
      const dialog = guest.getByRole('dialog', { name: '로그인이 필요합니다' });
      await expect(dialog.getByText('로그인이 필요합니다. 로그인 하시겠습니까?')).toBeVisible();
      await dialog.getByRole('button', { name: '로그인' }).click();
      await dialog.getByLabel('이메일').fill(viewer.email);
      await dialog.getByLabel('비밀번호').fill(viewer.password);
      await dialog.getByRole('button', { name: '로그인' }).click();
      await expect(dialog).toBeHidden();
      await expect(guest).toHaveURL(new RegExp(`/lives/${stream.id}$`));
      await expect(guest.getByRole('button', { name: '로그아웃' })).toBeVisible();
      await expect(guestInput).toHaveValue(draft);
      await expect(guestInput).toBeFocused();
      await guest.getByRole('button', { name: '채팅 전송' }).click();
      await expect(member.getByRole('list', { name: '채팅 메시지' }).getByText(draft)).toBeVisible();
    } finally {
      await guestContext.close();
      await memberContext.close();
    }
  });
});

test.describe('판매자 송출', () => {
  test.skip(!viewer.email || !viewer.password, 'E2E_EMAIL, E2E_PASSWORD 환경변수가 필요합니다.');
  test.skip(seller.email === viewer.email, '판매자와 시청자가 다른 계정이어야 합니다. (E2E_SELLER_EMAIL, E2E_SELLER_PASSWORD)');

  test('스튜디오에서 가짜 카메라로 방송을 시작하면 시청 화면에 영상이 나온다', async ({ browser, request }) => {
    test.setTimeout(120_000);
    const sellerContext = await browser.newContext();
    const viewerContext = await browser.newContext();
    const studio = await sellerContext.newPage();
    const watch = await viewerContext.newPage();
    let endAfter = false;
    // 방송 중에 연 경매의 상품명 (방송이 끝난 뒤 경매 목록 표시를 확인)
    let liveProductName: string | undefined;

    // 미리 만들어 둔 경매(시작 전). 스튜디오에서 [이 방송에 올리기]로 방송에 보여준다. 카테고리가 없으면 건너뛴다.
    // 판매자 화면과 로그인 쿠키가 섞이지 않도록 따로 된 요청(request)으로 만든다.
    const scheduled = await prepareScheduledAuction(request);

    try {
      // 판매자: 스튜디오. 종료되지 않은 방송이 있으면 그 방송을 쓰고, 없으면 만든다.
      await loginOnPage(studio, seller);
      // 판매자 인증을 받은 판매자에게만 [방송하기] 탭이 보인다.
      await expect(studio.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '방송하기' })).toBeVisible();
      // 방송 시작 전에는 스튜디오에 시청 화면 링크가 없어, 스튜디오가 받는 응답에서 방송 ID를 읽는다.
      // (API로 판매자에게 다시 로그인하면 스튜디오의 로그인이 끊긴다)
      // 로그인 직후 다른 화면(홈)도 같은 요청을 보내고, 화면을 옮기면 그 응답 내용은 읽을 수 없다.
      // 그래서 스튜디오를 연 뒤 새로고침하면서 오는 응답을 읽고, 그래도 못 읽으면 다시 새로고침한다.
      await studio.goto('/studio');
      let liveStreamId = await activeStreamIdOnStudio(studio);
      const createButton = studio.getByRole('button', { name: '방송 만들기' });
      const startButton = studio.getByRole('button', { name: '방송 시작' });
      const endButton = studio.getByRole('button', { name: '방송 종료' });
      await expect(createButton.or(startButton).or(endButton)).toBeVisible();
      const title = `E2E 송출 ${new Date().toISOString().slice(0, 19)}`;
      // 이 테스트가 방송을 새로 만들었을 때만 해시태그를 넣는다. (남아 있던 방송을 이어 쓰면 확인하지 않음)
      const tag = `E2E태그${Date.now() % 100000}`;
      let tagged = false;
      if (await createButton.isVisible()) {
        await studio.getByLabel('방송 제목 (100자 이하)').fill(title);
        // 해시태그: Enter로 더하고, 앞의 #은 빼고 저장된다.
        const hashtagInput = studio.getByLabel('해시태그 (선택, 5개까지)');
        // 형식이 맞지 않는 글이 남아 있으면 방송을 만들지 않고 오류를 보여 준다. (오류 난 글이 말없이 버려지지 않게)
        await hashtagInput.fill('한정판!');
        await createButton.click();
        await expect(studio.getByText('해시태그는 한글·영문·숫자·밑줄(_)로 20자까지 쓸 수 있어요.')).toBeVisible();
        await expect(createButton).toBeVisible();
        await hashtagInput.fill(`#${tag}`);
        await hashtagInput.press('Enter');
        await hashtagInput.fill('필름카메라');
        await hashtagInput.press('Enter');
        await expect(studio.getByRole('list', { name: '넣은 해시태그' }).getByRole('listitem')).toHaveCount(2);
        await test.info().attach('방송 만들기 (해시태그 입력)', { body: await studio.screenshot(), contentType: 'image/png' });
        const created = studio.waitForResponse((r) => r.url().endsWith('/api/v1/live-streams') && r.request().method() === 'POST');
        await createButton.click();
        liveStreamId = (await (await created).json()).data?.id;
        endAfter = true;
        tagged = true;
      }

      // 방송 전 미리보기: 가짜 카메라 영상이 나온다.
      const preview = studio.getByLabel('내 카메라 미리보기');
      await expect.poll(() => videoWidth(preview), { timeout: 20_000 }).toBeGreaterThan(0);

      // 시청자가 방송 예정 화면에서 기다리다가, 판매자가 시작하면 새로고침 없이 영상이 나오고 채팅에 시작 안내가 올라온다.
      let waitedBeforeStart = false;
      if (await startButton.isVisible()) {
        expect(liveStreamId, '판매자의 방송을 찾지 못했습니다.').toBeDefined();
        await loginOnPage(watch, viewer);
        await watch.goto(`/lives/${liveStreamId}`);
        await expect(watch.getByText('아직 방송 전이에요')).toBeVisible();
        waitedBeforeStart = true;

        await expect(startButton).toBeEnabled();
        await startButton.click();
        endAfter = true;
      }
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible({ timeout: 20_000 });
      const watchHref = await studio.getByRole('link', { name: '시청 화면 열기' }).getAttribute('href');
      expect(watchHref).toMatch(/^\/lives\/\d+$/);

      // 시청자: 같은 방송에서 판매자 영상이 재생된다.
      if (waitedBeforeStart) {
        await expect(watch.getByRole('list', { name: '채팅 메시지' }).getByText('방송이 시작되었습니다.')).toBeVisible();
      } else {
        await loginOnPage(watch, viewer);
        await watch.goto(watchHref ?? '/');
      }
      const video = watch.locator('video');
      await expect.poll(() => videoWidth(video), { timeout: 30_000 }).toBeGreaterThan(0);
      await expect(watch.getByText('판매자 화면을 기다리고 있어요')).toBeHidden();
      expect(watch.url(), '방송이 시작돼도 시청 화면 주소는 그대로다').toContain(watchHref ?? '');

      // 시청자가 다른 화면으로 가도 작은 플레이어에서 판매자 영상이 이어지고, 돌아오면 시청 화면에서 다시 본다.
      await watch.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '경매', exact: true }).click();
      const viewerMini = watch.getByRole('complementary', { name: '보던 방송' });
      await expect.poll(() => videoWidth(viewerMini.locator('video')), { timeout: 15_000 }).toBeGreaterThan(0);
      await test.info().attach('시청자 작은 플레이어', { body: await watch.screenshot(), contentType: 'image/png' });
      await viewerMini.getByRole('link', { name: '방송으로 돌아가기' }).click();
      await expect(viewerMini).toBeHidden();
      await expect.poll(() => videoWidth(video), { timeout: 15_000 }).toBeGreaterThan(0);

      if (tagged) {
        // 해시태그는 시청 화면 제목 아래와 방송 목록 카드에 나온다.
        await expect(watch.getByRole('list', { name: '해시태그' }).getByText(`#${tag}`)).toBeVisible();
        const listPage = await viewerContext.newPage();
        await listPage.goto('/lives');
        const card = listPage.getByRole('link').filter({ hasText: title });
        await expect(card.getByText(`#${tag}`)).toBeVisible();
        await expect(card.getByText('#필름카메라')).toBeVisible();
        await test.info().attach('방송 목록 (해시태그)', { body: await listPage.screenshot(), contentType: 'image/png' });
        await listPage.close();
      }

      // 판매자 화면에 시청자 수가 반영된다.
      await expect(studio.getByText(/시청\s*1\s*명/)).toBeVisible({ timeout: 20_000 });

      // 판매자가 새로고침하면 "송출이 멈춰요" 확인 창이 뜨고, 새로고침해도 송출 토큰을 다시 받아(/publisher-token) 이어서 송출한다.
      let askedBeforeUnload = false;
      studio.once('dialog', (dialog) => {
        askedBeforeUnload = dialog.type() === 'beforeunload';
        void dialog.accept();
      });
      await studio.reload();
      expect(askedBeforeUnload, '방송 중 새로고침 전에 확인 창이 떠야 합니다.').toBe(true);
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

      // 판매자가 채팅 공지를 올리면 시청 화면 채팅창 위에 고정된다. 시청자가 닫아도 다시 들어오면 다시 보인다.
      const noticeText = `E2E 공지 ${Date.now()}`;
      await studio.getByLabel('채팅창 위에 고정할 공지').fill(noticeText);
      // 이어 쓰는 방송에 이미 공지가 있으면 버튼이 [공지 바꾸기]다.
      await studio.getByRole('button', { name: /^공지 (올리기|바꾸기)$/ }).click();
      const watchNotice = watch.getByRole('note', { name: '판매자 공지' });
      await expect(watchNotice).toContainText(noticeText);
      await watch.getByRole('button', { name: '공지 닫기' }).click();
      await expect(watchNotice).toBeHidden();
      await watch.reload();
      await expect(watch.getByRole('note', { name: '판매자 공지' })).toContainText(noticeText);
      await expect.poll(() => videoWidth(watch.locator('video')), { timeout: 30_000 }).toBeGreaterThan(0);
      await studio.getByRole('button', { name: '공지 내리기' }).click();
      await expect(watch.getByRole('note', { name: '판매자 공지' })).toBeHidden();

      // 방송 중 다른 화면(경매 목록)으로 가도 송출이 이어지고, 작은 방송 창이 뜬다.
      await studio.getByRole('navigation', { name: '내 메뉴' }).getByRole('link', { name: '경매', exact: true }).click();
      await expect(studio).toHaveURL(/\/auctions$/);
      const mini = studio.getByRole('complementary', { name: '방송 중' });
      await expect(mini).toBeVisible();
      await expect.poll(() => videoWidth(mini.getByLabel('내 카메라 화면')), { timeout: 10_000 }).toBeGreaterThan(0);
      // 시청자 영상은 끊기지 않고 계속 재생된다.
      const playedAt = await videoTime(video);
      await expect.poll(() => videoTime(video), { timeout: 10_000 }).toBeGreaterThan(playedAt + 1);
      await expect(watch.getByText('판매자 화면을 기다리고 있어요')).toBeHidden();
      // 스튜디오를 떠나 있는 동안 온 채팅 수가 보인다.
      await watch.getByLabel('채팅 입력').fill(`E2E 자리 비운 동안 채팅 ${Date.now()}`);
      await watch.getByRole('button', { name: '채팅 전송' }).click();
      await expect(mini.getByText(/새 채팅\s*1/)).toBeVisible();
      await test.info().attach('다른 화면의 작은 방송 창', { body: await studio.screenshot(), contentType: 'image/png' });
      await mini.getByRole('link', { name: '스튜디오로 돌아가기' }).click();
      await expect(studio.getByText('LIVE 송출 중')).toBeVisible();

      // 스튜디오를 떠나지 않고 상품을 등록하면 그 상품의 경매 조건 입력이 바로 열리고, 경매를 시작할 수 있다.
      await studio.getByRole('button', { name: '상품 등록' }).click();
      const productDialog = studio.getByRole('dialog', { name: '상품 등록' });
      const category = productDialog.getByLabel('카테고리');
      await expect(category.or(productDialog.getByText('아직 상품을 등록할 카테고리가 없어요'))).toBeVisible();
      if (await category.isVisible()) {
        await category.selectOption((await category.locator('option').nth(1).getAttribute('value')) ?? '');
        const productName = `E2E 방송 상품 ${Date.now()}`;
        liveProductName = productName;
        await productDialog.getByLabel('상품명 (50자 이하)').fill(productName);
        await productDialog.getByRole('button', { name: '상품 등록' }).click();
        await expect(productDialog).toBeHidden();
        const conditions = studio.getByRole('form', { name: `${productName} 경매 조건` });
        await conditions.getByLabel('시작가', { exact: true }).fill('10000');
        await conditions.getByLabel('최소 입찰 단위').fill('1000');
        await conditions.getByRole('button', { name: '이 조건으로 경매 시작' }).click();
        await expect(studio.getByRole('list', { name: '이 방송의 경매' }).getByText(productName)).toBeVisible();
        // 시청 화면: 시작한 경매가 바로 경매 목록에 나오고, 채팅에 시작 안내가 올라온다.
        await expect(watch.getByRole('list', { name: '이 방송의 경매' }).getByText(productName)).toBeVisible();
        await expect(watch.getByRole('list', { name: '채팅 메시지' }).getByText(`‘${productName}’ 경매가 시작되었습니다`, { exact: false })).toBeVisible();
        // 시청자는 방송을 떠나지 않고 입찰 창에서 금액을 정한다.
        await watch.getByRole('button', { name: `${productName} 입찰하기` }).click();
        const bidDialog = watch.getByRole('dialog', { name: '입찰하기' });
        await expect(bidDialog.getByText(productName)).toBeVisible();
        await bidDialog.getByRole('button', { name: '최소' }).click();
        await expect(bidDialog.getByLabel('입찰 금액')).toHaveValue('10,000');
        await expect(watch).toHaveURL(/\/lives\/\d+$/);
        await bidDialog.getByRole('button', { name: '닫기' }).click();
        await expect(bidDialog).toBeHidden();
        // 진행 중인 경매를 영상 위에 띄우면 시청 화면 영상 위에서도 바로 입찰 창을 열 수 있다.
        await studio.getByRole('button', { name: `${productName} 영상에 띄우기` }).click();
        await watch.getByRole('region', { name: '영상 위 경매' }).getByRole('button', { name: `${productName} 입찰하기` }).click();
        await expect(bidDialog.getByText(productName)).toBeVisible();
        await bidDialog.getByRole('button', { name: '닫기' }).click();
      } else {
        await productDialog.getByRole('button', { name: '닫기' }).click();
      }

      if (scheduled) {
        // 미리 만든 경매는 새로 열 수 없고, [이 방송에 올리기]로 방송에 보여준다.
        const row = studio.getByRole('region', { name: '경매 상품' }).getByRole('listitem').filter({ hasText: scheduled.name });
        await row.getByRole('button', { name: '이 방송에 올리기' }).click();
        await expect(row.getByText(/이 방송 ·/)).toBeVisible();
        await expect(watch.getByRole('list', { name: '이 방송의 경매' }).getByText(scheduled.name)).toBeVisible();
        await expect(watch.getByRole('list', { name: '채팅 메시지' }).getByText(`‘${scheduled.name}’ 경매가 방송에 올라왔습니다`, { exact: false })).toBeVisible();
        await test.info().attach('시청 화면 (이 방송의 경매)', { body: await watch.screenshot(), contentType: 'image/png' });

        // 판매자가 경매를 영상 위에 띄우면 시청 화면 영상 위에 바로 나오고(먼저 띄운 경매와 함께 2개까지), 내리면 사라진다.
        // 스튜디오 경매 패널은 기본이 [진행 중] 탭이라, 시작 전 경매는 [시작 전] 탭에서 띄운다.
        await studio.getByRole('tablist', { name: '경매 상태' }).getByRole('tab', { name: /^시작 전/ }).click();
        await studio.getByRole('button', { name: `${scheduled.name} 영상에 띄우기` }).click();
        await expect(studio.getByRole('region', { name: '영상 위 경매' }).getByText(scheduled.name)).toBeVisible();
        const overlays = watch.getByRole('region', { name: '영상 위 경매' });
        const overlay = overlays.filter({ hasText: scheduled.name });
        await expect(overlay).toBeVisible();
        await expect(overlays).toHaveCount(liveProductName ? 2 : 1);
        await expect(overlay.getByText('시작가')).toBeVisible();
        await test.info().attach('시청 화면 (영상 위 경매)', { body: await watch.screenshot(), contentType: 'image/png' });
        await test.info().attach('스튜디오 (영상 위 경매)', { body: await studio.screenshot(), contentType: 'image/png' });
        await studio.getByRole('button', { name: `${scheduled.name} 영상에서 내리기` }).click();
        await expect(overlay).toBeHidden();
      }

      // 방송 중에 연 경매의 상세: 방송 중이면 "LIVE 방송 보러 가기"가 나온다. (방송이 끝나면 아래에서 "방송 종료"로 바뀌는지 본다)
      let auctionPage: Page | undefined;
      if (endAfter && liveProductName) {
        auctionPage = await viewerContext.newPage();
        await auctionPage.goto('/auctions');
        await auctionPage.getByRole('searchbox', { name: '상품명' }).fill(liveProductName);
        await auctionPage.getByRole('main').getByRole('button', { name: '검색' }).click();
        await auctionPage.getByRole('link', { name: new RegExp(escapeRegExp(liveProductName)) }).click();
        await expect(auctionPage.getByRole('main').getByRole('link', { name: /LIVE\s*방송 보러 가기/ })).toBeVisible();
      }

      if (endAfter) {
        // 방송 종료: 확인 창에서 종료하면 시청 화면에도 종료가 나온다.
        await endButton.click();
        await studio.getByRole('dialog').getByRole('button', { name: '방송 종료' }).click();
        await expect(studio.getByText(/방송을 종료했어요/)).toBeVisible();
        endAfter = false;
        await expect(watch.getByText('방송이 끝났어요')).toBeVisible({ timeout: 20_000 });

        if (liveProductName) {
          // 방송에서 연 경매는 방송이 끝나도 이어지지만, 경매 목록에서 "방송 중 경매"로 나오지 않는다.
          await watch.goto('/auctions');
          await watch.getByRole('searchbox', { name: '상품명' }).fill(liveProductName);
          await watch.getByRole('main').getByRole('button', { name: '검색' }).click();
          const card = watch.getByRole('link', { name: new RegExp(escapeRegExp(liveProductName)) });
          await expect(card).toBeVisible();
          await expect(card.getByText('방송 중 경매')).toBeHidden();
        }
        if (auctionPage) {
          // 열어 둔 경매 상세는 다시 불러오면서 방송 링크 대신 "방송이 끝났어요" 안내로 바뀐다.
          await expect(auctionPage.getByText('이 경매가 소개된 방송은 끝났어요.')).toBeVisible({ timeout: 15_000 });
          await expect(auctionPage.getByRole('main').getByRole('link', { name: /방송 보러 가기/ })).toBeHidden();
          await test.info().attach('경매 상세 (방송 종료)', { body: await auctionPage.screenshot(), contentType: 'image/png' });
          await auctionPage.close();
        }
      }
    } finally {
      if (endAfter) {
        // 테스트가 중간에 실패해도 이 테스트가 시작한 방송은 정리한다.
        await endActiveStream(studio.request);
      }
      if (scheduled) await apiCall(request, 'POST', `/api/v1/auctions/${scheduled.auctionId}/cancel`, scheduled.token);
      await sellerContext.close();
      await viewerContext.close();
    }
  });
});

/**
 * 스튜디오를 새로고침하면서 받는 "내 방송" 응답에서 방송 ID를 읽는다. 방송이 없으면 undefined.
 * 새로고침 전 화면이 보낸 같은 요청을 잡으면 응답 내용을 읽을 수 없어, 그때는 다시 새로고침한다. (최대 3번)
 */
async function activeStreamIdOnStudio(studio: Page): Promise<number | undefined> {
  for (let attempt = 0; attempt < 3; attempt++) {
    const response = studio.waitForResponse((r) => r.url().includes('/api/v1/live-streams/me/active') && r.ok());
    await studio.reload();
    try {
      return ((await (await response).json()) as Envelope<LiveStream>).data?.id;
    } catch {
      // 이전 화면의 응답이었다. 다시 새로고침한다.
    }
  }
  throw new Error('스튜디오에서 내 방송 정보를 읽지 못했습니다.');
}

async function loginOnPage(page: Page, account: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

/** 영상 요소의 재생 위치(초). 재생이 이어지는지 볼 때 쓴다. */
async function videoTime(locator: Locator) {
  return locator.first().evaluate((element) => (element as unknown as { currentTime: number }).currentTime).catch(() => 0);
}

/** 영상 요소의 실제 영상 폭. 영상이 아직 나오지 않으면 0 */
async function videoWidth(locator: Locator) {
  return locator.first().evaluate((element) => (element as unknown as { videoWidth: number }).videoWidth).catch(() => 0);
}

type Category = { id: number; children?: Category[] };

/** 판매자 상품을 하나 만들고 30분 뒤 시작하는 경매를 잡는다. 소분류 카테고리가 없으면 undefined */
async function prepareScheduledAuction(request: APIRequestContext) {
  const login = await apiCall<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, seller);
  const token = login.body.data?.accessToken ?? '';
  const categories = (await apiCall<Category[]>(request, 'GET', '/api/v1/categories')).body.data ?? [];
  const leaf = categories.flatMap((c) => c.children ?? [])[0];
  if (!token || !leaf) return undefined;

  const name = `E2E 예약 경매 ${Date.now()}`;
  const product = await apiCall<{ id: number }>(request, 'POST', '/api/v1/products', token, { categoryId: leaf.id, name, description: 'Playwright E2E 테스트가 만든 상품입니다.' });
  expect(product.ok, `상품 등록 실패: ${product.body.error?.message}`).toBe(true);
  const startAt = serverTime(30);
  const auction = await apiCall<{ id: number }>(request, 'POST', '/api/v1/auctions', token, {
    productId: product.body.data?.id,
    startPrice: 20000,
    bidUnit: 1000,
    startAt,
    endAt: serverTime(90),
  });
  expect(auction.ok, `경매 등록 실패: ${auction.body.error?.message}`).toBe(true);
  return { name, token, auctionId: auction.body.data?.id ?? 0 };
}

/** 지금부터 minutes분 뒤의 서버 시각 문자열 (시간대 없는 이 컴퓨터 시각, 예: 2026-10-21T15:30:00) */
function serverTime(minutes: number) {
  const d = new Date(Date.now() + minutes * 60_000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:00`;
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
