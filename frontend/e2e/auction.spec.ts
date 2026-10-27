import { expect, test, type Page } from '@playwright/test';

/*
 * 판매자: 상품 등록 + 경매 일정 → 경매 상세 → 경매 목록 검색 → 경매 취소
 *
 * 로컬 백엔드가 떠 있어야 하고, 상품을 등록할 소분류 카테고리가 하나 이상 있어야 한다. (관리자 화면 /admin/categories에서 만든다)
 * 계정은 E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (없으면 E2E_EMAIL / E2E_PASSWORD). (frontend/README.md "E2E 테스트")
 */

const seller = {
  email: process.env.E2E_SELLER_EMAIL || process.env.E2E_EMAIL || '',
  password: process.env.E2E_SELLER_PASSWORD || process.env.E2E_PASSWORD || '',
};

test.describe('경매 등록', () => {
  test.skip(!seller.email || !seller.password, 'E2E_SELLER_EMAIL, E2E_SELLER_PASSWORD 환경변수가 필요합니다.');

  test('상품을 등록하며 경매 일정을 잡고, 목록에서 찾은 뒤 취소한다', async ({ page, browser }) => {
    await login(page);
    await page.goto('/products/new');

    const category = page.getByLabel('카테고리');
    await expect(category.or(page.getByText('아직 상품을 등록할 카테고리가 없어요'))).toBeVisible();
    test.skip(!(await category.isVisible()), '소분류 카테고리가 없어 상품을 등록할 수 없습니다. (/admin/categories)');

    // 상품 정보: 첫 번째 소분류
    const firstCategory = await category.locator('option').nth(1).getAttribute('value');
    await category.selectOption(firstCategory ?? '');
    const name = `E2E 상품 ${Date.now()}`;
    await page.getByLabel('상품명 (50자 이하)').fill(name);
    await page.getByLabel('상품 설명').fill('Playwright E2E 테스트가 등록한 상품입니다.');

    // 경매 일정: 10분 뒤 시작, 1시간 진행
    await page.getByText('경매 일정 바로 잡기').click();
    await page.getByLabel('시작가', { exact: true }).fill('10000');
    await page.getByLabel('최소 입찰 단위').fill('1000');
    await page.getByLabel('시작 시각', { exact: true }).fill(inputTime(10));
    await page.getByLabel('마감 시각', { exact: true }).fill(inputTime(70));
    await page.getByRole('button', { name: '상품 등록하고 경매 열기' }).click();

    // 경매 상세
    await expect(page).toHaveURL(/\/auctions\/\d+$/);
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();
    await expect(page.getByText('10,000 P').first()).toBeVisible();
    const detailUrl = page.url();

    // 로그인하지 않아도 경매 상세를 볼 수 있고, [판매자에게 문의]를 누르면 로그인 창이 뜬다.
    const guestContext = await browser.newContext();
    const guest = await guestContext.newPage();
    await guest.goto(detailUrl);
    await expect(guest.getByRole('heading', { level: 1, name })).toBeVisible();
    await guest.getByRole('link', { name: '판매자에게 문의' }).click();
    await expect(guest.getByRole('dialog', { name: '로그인이 필요합니다' })).toBeVisible();
    await guest.getByRole('dialog').getByRole('button', { name: '취소' }).click();
    await expect(guest).toHaveURL(detailUrl);
    await guestContext.close();

    // 경매 목록에서 상품명으로 찾는다.
    await page.goto('/auctions');
    await page.getByRole('searchbox', { name: '상품명' }).fill(name);
    await page.getByRole('main').getByRole('button', { name: '검색' }).click();
    await page.getByRole('link', { name: new RegExp(name) }).click();
    await expect(page).toHaveURL(detailUrl);

    // 취소: 확인 창을 거쳐 취소됨으로 바뀐다.
    await page.getByRole('button', { name: '경매 취소' }).click();
    await page.getByRole('dialog').getByRole('button', { name: '경매 취소' }).click();
    await expect(page.getByText('취소됨').first()).toBeVisible();
  });
});

async function login(page: Page) {
  await page.goto('/login');
  await page.getByLabel('이메일').fill(seller.email);
  await page.getByLabel('비밀번호').fill(seller.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
}

/** 지금부터 minutes분 뒤를 datetime-local 입력값으로 */
function inputTime(minutes: number) {
  const date = new Date(Date.now() + minutes * 60 * 1000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
