import { expect, test, type APIRequestContext } from '@playwright/test';

/*
 * 주문 화면의 내 리뷰: 새로고침해도 서버에서 다시 불러와 [수정]·[삭제]가 유지된다.
 * 리뷰 없음 → 쓰기 → F5 → 수정 → F5 → 삭제 → F5
 *
 * 로컬 백엔드가 떠 있어야 하고, 구매자(E2E_EMAIL)에게 구매 확정한 주문이 하나 있어야 한다. (frontend/README.md "E2E 테스트")
 * 리뷰가 없는 주문을 골라 쓰고 지우므로, 이미 있던 리뷰는 건드리지 않는다.
 */

const buyer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };

type Data<T> = { data?: T };

/** 구매 확정했고 아직 리뷰가 없는 주문. 있던 리뷰는 건드리지 않는다. */
async function completedOrderWithoutReview(request: APIRequestContext): Promise<number | undefined> {
  const login = await request.post('/api/v1/auth/login', { data: buyer });
  await expect(login, '구매자로 로그인하지 못했습니다.').toBeOK();
  const token = ((await login.json()) as Data<{ accessToken?: string }>).data?.accessToken;
  expect(token, '로그인 응답에 토큰이 없습니다.').toBeTruthy();
  const headers = { Authorization: `Bearer ${token}` };

  const orders = await request.get('/api/v1/orders', { headers, params: { role: 'BUYER', status: 'COMPLETED', size: 50 } });
  await expect(orders, '주문 목록을 불러오지 못했습니다.').toBeOK();
  const content = ((await orders.json()) as Data<{ content?: { orderId?: number }[] }>).data?.content ?? [];

  for (const { orderId } of content) {
    if (orderId === undefined) continue;
    const review = await request.get(`/api/v1/reviews/orders/${orderId}`, { headers });
    if (review.status() === 404 && ((await review.json()) as { error?: { code?: string } }).error?.code === 'REVIEW_001') return orderId;
    await expect(review, '주문 리뷰를 불러오지 못했습니다.').toBeOK();
  }
  return undefined;
}

test.describe('주문 리뷰 새로고침', () => {
  test.skip(!buyer.email, '구매자 계정이 필요합니다. (E2E_EMAIL)');

  test('쓴 리뷰는 새로고침해도 보이고, 고치거나 지운 결과도 유지된다', async ({ page, request }) => {
    const orderId = await completedOrderWithoutReview(request);
    test.skip(orderId === undefined, '리뷰를 안 쓴 구매 확정 주문이 없습니다.');

    await page.goto('/login');
    await page.getByLabel('이메일').fill(buyer.email);
    await page.getByLabel('비밀번호').fill(buyer.password);
    await page.getByRole('button', { name: '로그인', exact: true }).click();
    await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();

    const section = page.getByRole('region', { name: '내 리뷰' });

    // 리뷰를 안 쓴 주문: 에러 없이 [리뷰 쓰기]만 보인다.
    await page.goto(`/orders/${orderId}`);
    await expect(section.getByRole('button', { name: '리뷰 쓰기' })).toBeVisible();
    await expect(section.getByRole('alert')).toHaveCount(0);

    // 쓰고 새로고침해도 내 리뷰와 [수정]·[삭제]가 보인다.
    const first = `E2E 리뷰 ${Date.now()}`;
    await section.getByRole('button', { name: '리뷰 쓰기' }).click();
    const writeDialog = page.getByRole('dialog', { name: '리뷰 쓰기' });
    await writeDialog.getByRole('radio', { name: '4점' }).click();
    await writeDialog.getByRole('textbox', { name: '내용 (선택)' }).fill(first);
    await writeDialog.getByRole('button', { name: '리뷰 남기기' }).click();
    await expect(section.getByText(first)).toBeVisible();
    await page.reload();
    await expect(section.getByText(first)).toBeVisible();
    await expect(section.getByRole('button', { name: '수정' })).toBeVisible();
    await expect(section.getByRole('button', { name: '삭제' })).toBeVisible();
    await expect(section.getByRole('button', { name: '리뷰 쓰기' })).toHaveCount(0);

    // 새로고침 뒤 고치고, 다시 새로고침해도 고친 내용이 남는다.
    const edited = `${first} (수정)`;
    await section.getByRole('button', { name: '수정' }).click();
    const editDialog = page.getByRole('dialog', { name: '리뷰 수정' });
    await editDialog.getByRole('textbox', { name: '내용 (선택)' }).fill(edited);
    await editDialog.getByRole('button', { name: '수정하기' }).click();
    await expect(section.getByText(edited)).toBeVisible();
    await page.reload();
    await expect(section.getByText(edited)).toBeVisible();

    // 새로고침 뒤 지우면 [리뷰 쓰기]로 돌아오고, 다시 새로고침해도 그대로다.
    await section.getByRole('button', { name: '삭제' }).click();
    await page.getByRole('dialog', { name: '리뷰를 삭제할까요?' }).getByRole('button', { name: '삭제' }).click();
    await expect(section.getByRole('button', { name: '리뷰 쓰기' })).toBeVisible();
    await page.reload();
    await expect(section.getByRole('button', { name: '리뷰 쓰기' })).toBeVisible();
    await expect(section.getByText(edited)).toHaveCount(0);
  });
});
