import { expect, test, type Page } from '@playwright/test';

/*
 * 판매자: 상품 이미지(여러 장·순서 바꾸기·지우기)와 설명 속 이미지 → 내 상품 대표 이미지 → 수정 → 경매 상세 갤러리·설명
 *
 * 로컬 백엔드가 떠 있어야 하고, 소분류 카테고리가 하나 이상 있어야 한다. (관리자 화면 /admin/categories)
 * 계정은 E2E_SELLER_EMAIL / E2E_SELLER_PASSWORD (없으면 E2E_EMAIL / E2E_PASSWORD). (frontend/README.md "E2E 테스트")
 */

const seller = {
  email: process.env.E2E_SELLER_EMAIL || process.env.E2E_EMAIL || '',
  password: process.env.E2E_SELLER_PASSWORD || process.env.E2E_PASSWORD || '',
};

test.describe('상품 이미지', () => {
  test.skip(!seller.email || !seller.password, 'E2E_SELLER_EMAIL, E2E_SELLER_PASSWORD 환경변수가 필요합니다.');

  test('이미지를 올려 순서를 바꾸고, 설명 사이에 이미지를 넣고, 수정에서 지운 뒤 경매 상세에서 본다', async ({ page }) => {
    await login(page);
    await page.goto('/products/new');
    const category = page.getByLabel('카테고리');
    await expect(category.or(page.getByText('아직 상품을 등록할 카테고리가 없어요'))).toBeVisible();
    test.skip(!(await category.isVisible()), '소분류 카테고리가 없어 상품을 등록할 수 없습니다. (/admin/categories)');
    await category.selectOption((await category.locator('option').nth(1).getAttribute('value')) ?? '');
    const name = `E2E 이미지 상품 ${Date.now()}`;
    await page.getByLabel('상품명 (50자 이하)').fill(name);

    // 갤러리: 두 장을 한 번에 올리고, 두 번째를 앞으로 옮겨 대표 이미지로
    await page.getByLabel('상품 이미지 파일').setInputFiles([await png(page, '#f5c84c', '1번'), await png(page, '#4c7cf5', '2번')]);
    const gallery = page.getByRole('list', { name: '상품 이미지 목록' }).getByRole('img');
    await expect(gallery).toHaveCount(2);
    const [first, second] = await gallery.evaluateAll((images) => images.map((image) => image.getAttribute('src')));
    await page.getByRole('button', { name: '상품 이미지 2 앞으로' }).click();
    await expect(gallery.first()).toHaveAttribute('src', second ?? '');
    await expect(gallery.nth(1)).toHaveAttribute('src', first ?? '');

    // 설명: 첫 줄 뒤(둘째 줄 앞)에 이미지를 넣는다. HTML은 글자로 남는다.
    const description = page.getByLabel('상품 설명', { exact: true });
    await description.fill('첫 줄 설명\n둘째 줄 설명\n<b>굵게</b><script>alert(1)</script>');
    await description.press('Control+Home');
    await description.press('ArrowDown');
    await page.getByLabel('설명에 넣을 이미지 파일').setInputFiles(await png(page, '#3fb37f', '설명'));
    await expect(page.getByRole('img', { name: '설명 속 이미지' })).toBeVisible();
    await expect(description).toHaveValue('첫 줄 설명');
    await expect(page.getByLabel('상품 설명 (이어서)')).toHaveValue('둘째 줄 설명\n<b>굵게</b><script>alert(1)</script>');
    await page.screenshot({ path: 'test-results/product-images-form.png', fullPage: true });

    await page.getByRole('button', { name: '상품 등록' }).click();
    await expect(page).toHaveURL(/\/me\/products/);

    // 내 상품: 대표 이미지(옮긴 두 번째 사진)
    const row = page.getByRole('listitem').filter({ hasText: name });
    await expect(row.getByRole('img', { name })).toHaveAttribute('src', second ?? '');
    const editHref = await row.getByRole('link', { name: '수정' }).getAttribute('href');
    const productId = editHref?.match(/\/products\/(\d+)\/edit/)?.[1];
    expect(productId).toBeTruthy();

    // 수정: 갤러리 두 번째(원래 1번)와 설명 속 이미지를 빼고, 설명 끝에 새 이미지를 넣는다.
    await row.getByRole('link', { name: '수정' }).click();
    await expect(gallery).toHaveCount(2);
    await page.getByRole('button', { name: '상품 이미지 2 지우기' }).click();
    await expect(gallery).toHaveCount(1);
    await page.getByLabel('상품 이미지 파일').setInputFiles(await png(page, '#9b6cf0', '3번'));
    await expect(gallery).toHaveCount(2);
    const added = await gallery.nth(1).getAttribute('src');
    const removedDescriptionImage = await page.getByRole('img', { name: '설명 속 이미지' }).getAttribute('src');
    await page.getByRole('button', { name: '설명에서 이미지 빼기' }).click();
    await expect(page.getByLabel('상품 설명', { exact: true })).toHaveValue('첫 줄 설명\n둘째 줄 설명\n<b>굵게</b><script>alert(1)</script>');
    await page.getByLabel('설명에 넣을 이미지 파일').setInputFiles(await png(page, '#e0535f', '새 설명'));
    await expect(page.getByRole('img', { name: '설명 속 이미지' })).toBeVisible();
    const descriptionImage = await page.getByRole('img', { name: '설명 속 이미지' }).getAttribute('src');
    await page.getByRole('button', { name: '저장' }).click();
    await expect(page).toHaveURL(/\/me\/products/);

    // 뺀 이미지는 저장소에서 지워진다. (저장 뒤 커밋되면)
    for (const removed of [first, removedDescriptionImage]) {
      await expect.poll(async () => (await page.request.get(removed ?? '')).status()).toBe(404);
    }

    // 경매 일정을 잡고 상세에서 본다.
    await page.goto(`/products/${productId}/auction`);
    await page.getByLabel('시작가', { exact: true }).fill('10000');
    await page.getByLabel('최소 입찰 단위').fill('1000');
    await page.getByLabel('시작 시각', { exact: true }).fill(inputTime(10));
    await page.getByLabel('마감 시각', { exact: true }).fill(inputTime(70));
    await page.getByRole('button', { name: '경매 열기' }).click();
    await expect(page).toHaveURL(/\/auctions\/\d+$/);

    const product = page.getByRole('region', { name: '상품' });
    await expect(product.getByRole('img', { name })).toHaveAttribute('src', second ?? '');
    // 작은 이미지를 누르면 큰 이미지가 바뀐다.
    await product.getByRole('button', { name: `${name} 이미지 2 보기` }).click();
    await expect(product.getByRole('img', { name })).toHaveAttribute('src', added ?? '');
    await expect(product.getByRole('img', { name: '상품 설명 이미지' })).toHaveAttribute('src', descriptionImage ?? '');
    await expect(product.getByText('<b>굵게</b><script>alert(1)</script>')).toBeVisible();
    await expect(product.locator('b, script')).toHaveCount(0);
    await page.screenshot({ path: 'test-results/product-images-detail.png', fullPage: true });

    // 목록 카드에도 대표 이미지
    await page.goto('/auctions');
    await page.getByRole('searchbox', { name: '상품명' }).fill(name);
    await page.getByRole('main').getByRole('button', { name: '검색' }).click();
    await expect(page.getByRole('link', { name: new RegExp(name) }).getByRole('img', { name })).toHaveAttribute('src', second ?? '');
    await page.getByRole('link', { name: new RegExp(name) }).click();

    // 정리: 경매 취소
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

/** 브라우저 캔버스로 색·글자가 들어간 PNG를 만든다. */
async function png(page: Page, color: string, label: string) {
  const base64 = await page.evaluate(
    ([fill, text]) => {
      const canvas = document.createElement('canvas');
      canvas.width = 640;
      canvas.height = 480;
      const context = canvas.getContext('2d');
      if (!context) return '';
      context.fillStyle = fill;
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.fillStyle = '#000';
      context.font = 'bold 96px sans-serif';
      context.fillText(text, 60, 280);
      return canvas.toDataURL('image/png').split(',')[1] ?? '';
    },
    [color, label] as const,
  );
  return { name: `${label}.png`, mimeType: 'image/png', buffer: Buffer.from(base64, 'base64') };
}

/** 지금부터 minutes분 뒤를 datetime-local 입력값으로 */
function inputTime(minutes: number) {
  const date = new Date(Date.now() + minutes * 60 * 1000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
