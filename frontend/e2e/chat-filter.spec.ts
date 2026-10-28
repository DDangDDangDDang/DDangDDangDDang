import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 채팅 필터
 * 1. 시청자가 외부 메신저 아이디를 보내려 하면 전송 전에 막히고 이유가 채팅창 아래에 나온다.
 *    같은 문장을 연달아 세 번 보내면 세 번째는 막힌다. (두 번까지는 채팅 목록에 나온다)
 * 2. 판매자는 스튜디오의 AI 검토 알림 카드에서 이 방송의 처리 방식(알림만 / 자동 가림 / 끄기)을 바꾼다. 다시 들어와도 유지된다.
 * 3. 관리자는 1:1 채팅 신고 화면의 [AI 검토] 목록을 본다.
 *
 * AI 판정 자체(자동 가림 → 시청자 화면 "가려진 메시지")는 API 키가 있어야 해서 백엔드 통합 테스트(ChatModerationIntegrationTest)로 확인한다.
 * 계정: E2E_EMAIL / E2E_PASSWORD(시청자), E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD(판매자), E2E_ADMIN_EMAIL / E2E_ADMIN_PASSWORD(관리자)
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };
const admin = { email: process.env.E2E_ADMIN_EMAIL ?? '', password: process.env.E2E_ADMIN_PASSWORD ?? '' };

type Envelope<T> = { data?: T; error?: { code?: string; message?: string } };
type LiveStream = { id: number; status: 'SCHEDULED' | 'LIVE' | 'ENDED' };

async function apiCall<T>(request: APIRequestContext, method: 'GET' | 'POST' | 'PATCH', path: string, token?: string, data?: unknown) {
  const response = await request.fetch(path, { method, data, headers: token ? { Authorization: `Bearer ${token}` } : undefined });
  const body = (await response.json().catch(() => ({}))) as Envelope<T>;
  return { ok: response.ok(), body };
}

async function login(page: Page, account: { email: string; password: string }) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

test.describe('채팅 필터', () => {
  test.skip(!viewer.email || !seller.email, 'E2E_EMAIL, E2E_SELLER_EMAIL 환경변수가 필요합니다.');

  let prepared: { stream: LiveStream; token: string; endAfter: boolean } | undefined;

  test.beforeAll(async ({ request }) => {
    const loginResult = await apiCall<{ accessToken: string }>(request, 'POST', '/api/v1/auth/login', undefined, seller);
    expect(loginResult.ok, `판매자 로그인 실패: ${loginResult.body.error?.message}`).toBe(true);
    const token = loginResult.body.data?.accessToken ?? '';
    let endAfter = false;
    let stream = (await apiCall<LiveStream>(request, 'POST', '/api/v1/live-streams', token, { title: `E2E 채팅 필터 ${Date.now()}` })).body.data;
    if (stream) endAfter = true;
    else stream = (await apiCall<LiveStream>(request, 'GET', '/api/v1/live-streams/me/active', token)).body.data;
    if (!stream) throw new Error('방송을 준비하지 못했습니다.');
    if (stream.status === 'SCHEDULED') {
      expect((await apiCall(request, 'POST', `/api/v1/live-streams/${stream.id}/start`, token)).ok).toBe(true);
      endAfter = true;
    }
    prepared = { stream, token, endAfter };
  });

  test.afterAll(async ({ request }) => {
    if (!prepared) return;
    // 다른 테스트에 영향이 없도록 처리 방식을 기본값으로 돌려 둔다.
    await apiCall(request, 'PATCH', `/api/v1/live-streams/${prepared.stream.id}`, prepared.token, { chatModerationMode: 'ALERT' });
    if (prepared.endAfter) await apiCall(request, 'POST', `/api/v1/live-streams/${prepared.stream.id}/end`, prepared.token);
  });

  test('외부 메신저 아이디와 같은 문장 반복은 보내기 전에 막고 이유를 보여 준다', async ({ page }) => {
    if (!prepared) throw new Error('방송이 준비되지 않았습니다.');
    await login(page, viewer);
    await page.goto(`/lives/${prepared.stream.id}`);

    const chatInput = page.getByLabel('채팅 입력');
    const send = page.getByRole('button', { name: '채팅 전송' });
    const chatList = page.getByRole('list', { name: '채팅 메시지' });
    await expect(chatInput).toBeEnabled({ timeout: 20_000 });

    await chatInput.fill('카톡 아이디 seller77 로 연락주세요');
    await send.click();
    await expect(page.getByText('외부 메신저 아이디는 채팅으로 주고받을 수 없습니다.')).toBeVisible();
    await expect(chatList.getByText('카톡 아이디 seller77')).toHaveCount(0);

    const repeated = `입찰 갑니다 ${Date.now()}`;
    for (let i = 0; i < 3; i++) {
      await chatInput.fill(repeated);
      await send.click();
      // 도배 제한(5초에 5개)에 걸리지 않도록 조금씩 쉬어 보낸다.
      await page.waitForTimeout(1200);
    }
    await expect(chatList.getByText(repeated)).toHaveCount(2);
    await expect(page.getByText('같은 내용을 연달아 보낼 수 없어요. 잠시 후에 보내 주세요.')).toBeVisible();
    await test.info().attach('채팅 필터 안내', { body: await page.screenshot(), contentType: 'image/png' });
  });

  test('판매자는 스튜디오에서 AI 검토 처리 방식을 바꾸고, 다시 들어와도 유지된다', async ({ page }) => {
    await login(page, seller);
    await page.goto('/studio');
    const select = page.getByLabel('AI가 문제 채팅을 찾으면');
    await expect(select).toBeVisible({ timeout: 20_000 });
    await expect(select).toHaveValue('ALERT');

    await select.selectOption('AUTO_HIDE');
    await expect(page.getByText(/명백한 채팅은 바로 가리고 알려 드려요/)).toBeVisible();
    await expect(select).toBeEnabled();

    await page.reload();
    await expect(page.getByLabel('AI가 문제 채팅을 찾으면')).toHaveValue('AUTO_HIDE', { timeout: 20_000 });
    await test.info().attach('AI 검토 설정', { body: await page.screenshot(), contentType: 'image/png' });
  });

  test('관리자는 1:1 채팅 신고 화면의 [AI 검토] 목록을 본다', async ({ page }) => {
    test.skip(!admin.email, 'E2E_ADMIN_EMAIL 환경변수가 필요합니다.');
    await login(page, admin);
    await page.goto('/admin/dm-reports');
    await page.getByRole('button', { name: 'AI 검토' }).click();
    await expect(page.getByRole('button', { name: 'AI 검토' })).toHaveAttribute('aria-pressed', 'true');
    const list = page.getByLabel('AI 검토 목록');
    await expect(list.getByText(/AI가 확인을 요청한 메시지가 없어요|“/).first()).toBeVisible();
    await test.info().attach('AI 검토 목록', { body: await page.screenshot(), contentType: 'image/png' });
  });
});
