import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

/*
 * 배송지 관리(/me/addresses): 추가 → 기본으로 지정 → 기본 배송지 삭제 시 다른 배송지가 기본 → 10개를 넘기면 안내
 *
 * 로컬 백엔드가 떠 있어야 한다. 시청자 계정(E2E_EMAIL)을 쓰고, 테스트가 만든 배송지만 지운 뒤 원래 기본 배송지를 되돌린다.
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const API = '/api/v1/users/me/addresses';

type Address = { addressId: number; addressName: string; isDefault: boolean };

test.describe('배송지 관리', () => {
  test.skip(!viewer.email, '시청자 계정이 필요합니다. (E2E_EMAIL)');

  test('추가·기본 지정·삭제가 되고, 10개를 넘기면 안내가 뜬다', async ({ page, request }) => {
    await login(page);
    // 화면과 같은 계정으로 API를 쓰면 리프레시 토큰이 바뀌어 화면이 로그아웃되므로, 화면이 받은 토큰을 그대로 쓴다.
    const auth = await accessTokenOf(page);
    const before = await list(request, auth);
    const originalDefault = before.find((a) => a.isDefault)?.addressId;
    const tag = `E2E${Date.now() % 100000}`;

    try {
      await page.goto('/me/addresses');
      await expect(page.getByRole('heading', { name: '배송지 관리', level: 1 })).toBeVisible();

      // 추가 (연락처는 하이픈이 자동으로 붙는다)
      await addInDialog(page, `${tag}-A`);
      await addInDialog(page, `${tag}-B`);
      const cardB = saved(page).filter({ hasText: `${tag}-B` });
      await expect(cardB.getByText('010-1234-5678')).toBeVisible();

      // 기본으로 지정
      await cardB.getByRole('button', { name: '기본으로 지정' }).click();
      await expect(cardB.getByText('기본 배송지')).toBeVisible();
      await expect(saved(page).first()).toContainText(`${tag}-B`);

      // 기본 배송지를 지우면 다른 배송지가 기본이 된다
      await cardB.getByRole('button', { name: `${tag}-B 삭제` }).click();
      await page.getByRole('dialog', { name: '배송지를 삭제할까요?' }).getByRole('button', { name: '삭제' }).click();
      await expect(saved(page).filter({ hasText: `${tag}-B` })).toHaveCount(0);
      await expect(saved(page).first().getByText('기본 배송지')).toBeVisible();

      // 10개까지 채운 뒤 11번째를 추가하면 안내가 뜬다
      const now = await list(request, auth);
      for (let i = now.length; i < 10; i++) {
        const res = await request.post(API, { headers: auth, data: { addressName: `${tag}-${i}`, receiverName: '테스트', receiverPhone: '0212345678', address: '서울시 테스트로 1' } });
        expect(res.status()).toBe(201);
      }
      await page.reload();
      await page.getByRole('button', { name: '배송지 추가' }).first().click();
      const dialog = page.getByRole('dialog', { name: '배송지 추가' });
      await fillAddress(dialog, `${tag}-11`);
      await dialog.getByRole('button', { name: '추가' }).click();
      await expect(dialog.getByText('배송지는 10개까지 저장할 수 있어요.', { exact: false })).toBeVisible();
    } finally {
      for (const a of await list(request, auth)) {
        if (a.addressName.startsWith(tag)) await request.delete(`${API}/${a.addressId}`, { headers: auth });
      }
      if (originalDefault) await request.patch(`${API}/${originalDefault}/default`, { headers: auth });
    }
  });
});

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(viewer.email);
  await page.getByLabel('비밀번호').fill(viewer.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

/** 화면이 로그인하며 받은 Access Token. (로그인 응답을 다시 받아 오지 않도록 재발급 API로 얻는다) */
async function accessTokenOf(page: Page): Promise<Record<string, string>> {
  const res = await page.request.post('/api/v1/auth/reissue');
  const token = ((await res.json()) as { data?: { accessToken?: string } }).data?.accessToken;
  expect(token, 'Access Token을 받지 못했습니다.').toBeTruthy();
  return { Authorization: `Bearer ${token}` };
}

async function list(request: APIRequestContext, auth: Record<string, string>): Promise<Address[]> {
  const res = await request.get(API, { headers: auth });
  return ((await res.json()) as { data?: Address[] }).data ?? [];
}

async function addInDialog(page: Page, name: string) {
  await page.getByRole('button', { name: '배송지 추가' }).first().click();
  const dialog = page.getByRole('dialog', { name: '배송지 추가' });
  await fillAddress(dialog, name);
  await dialog.getByRole('button', { name: '추가' }).click();
  await expect(dialog).toBeHidden();
  await expect(saved(page).filter({ hasText: name })).toBeVisible();
}

async function fillAddress(dialog: ReturnType<Page['getByRole']>, name: string) {
  await dialog.getByLabel('배송지 이름').fill(name);
  await dialog.getByLabel('받는 사람 이름').fill('테스트');
  await dialog.getByLabel('연락처').fill('01012345678');
  await dialog.getByLabel('주소').fill('서울시 중구 세종대로 1');
}

/** 저장된 배송지 목록의 항목들 (옆 메뉴의 목록과 섞이지 않게) */
function saved(page: Page) {
  return page.getByRole('list', { name: '저장된 배송지' }).getByRole('listitem');
}
