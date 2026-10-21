import { expect, test, type APIRequestContext } from '@playwright/test';

/*
 * 로그인 → 홈 → 방송 시청 → 채팅 전송
 *
 * 로컬 백엔드(MySQL·Redis·LiveKit 포함)가 떠 있어야 한다. 계정은 환경변수로 받는다. (frontend/README.md "E2E 테스트")
 * - E2E_EMAIL / E2E_PASSWORD: 시청자 계정 (화면에서 로그인)
 * - E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD: 방송을 여는 계정 (API로 방송을 만들고 시작). 없으면 시청자 계정으로 연다.
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

    // 로그인
    await page.goto('/login');
    await page.getByLabel('이메일').fill(viewer.email);
    await page.getByLabel('비밀번호').fill(viewer.password);
    await page.getByRole('button', { name: '로그인', exact: true }).click();
    await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();

    // 홈: 방송 섹션이 보이고, 전체 보기 → 검색으로 방금 연 방송을 찾는다.
    await page.goto('/');
    await expect(page.getByRole('heading', { name: '지금 인기 라이브' })).toBeVisible();
    await page.getByRole('link', { name: '전체 보기' }).first().click();
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

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
