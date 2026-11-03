import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test';

/*
 * 주문 상세: 기본 배송지 미리 채우기, 상대방에게 문의(1:1 채팅), 배송 중 자동 구매 확정 안내
 *
 * 로컬 백엔드와 시청자(E2E_EMAIL)·판매자(E2E_SELLER_EMAIL) 계정이 필요하다.
 * 시청자가 이미 가진 주문을 읽기만 한다. (배송지 입력·구매 확정은 누르지 않음) 알맞은 주문이 없으면 그 단계는 건너뛴다.
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

type Order = { orderId: number; status: string; buyerId: number; sellerId: number; address?: string };
type Address = { addressName?: string; receiverName?: string; address?: string } | null;

test.describe('주문 상세', () => {
  test.skip(!viewer.email || !seller.email, '시청자·판매자 계정이 필요합니다. (E2E_EMAIL, E2E_SELLER_EMAIL)');

  test('배송지를 처음 넣는 주문은 기본 배송지로 채워지고, 상대방에게 문의할 수 있고, 배송 중이면 자동 확정 안내가 보인다', async ({ browser }) => {
    const page = await openAs(browser, viewer);
    const auth = await accessTokenOf(page);
    const orders = await buyerOrders(page.request, auth);

    // 배송지 입력 전 주문: 기본 배송지로 미리 채움
    const defaultAddress = ((await (await page.request.get('/api/v1/users/me/addresses/default', { headers: auth })).json()) as { data?: Address }).data;
    const needAddress = orders.find((o) => o.status === 'ESCROW' && !o.address);
    if (needAddress && defaultAddress?.address) {
      await page.goto(`/orders/${needAddress.orderId}`);
      await expect(page.getByRole('heading', { name: '배송지 입력' })).toBeVisible();
      await expect(page.getByLabel('주소', { exact: true })).toHaveValue(defaultAddress.address);
      await expect(page.getByLabel('받는 사람 이름')).toHaveValue(defaultAddress.receiverName ?? '');
      await expect(page.getByLabel('배송지 선택')).toBeVisible();
    }

    // 배송 중: 자동 확정 안내 + 판매자에게 문의. E2E 판매자가 판 주문이 있으면 그 주문으로 판매자 화면까지 확인한다
    const sellerPage = await openAs(browser, seller);
    const sellerAuth = await accessTokenOf(sellerPage);
    const sellerId = ((await (await sellerPage.request.get('/api/v1/users/me', { headers: sellerAuth })).json()) as { data?: { userId?: number } }).data?.userId;
    const shippingOrders = orders.filter((o) => o.status === 'SHIPPING');
    const shipping = shippingOrders.find((o) => o.sellerId === sellerId) ?? shippingOrders[0];
    test.skip(!shipping, '배송 중인 주문이 없어 나머지를 건너뜁니다.');
    if (!shipping) return;
    await page.goto(`/orders/${shipping.orderId}`);
    await expect(page.getByText('발송 후 7일이 지나면 자동으로 구매 확정돼요', { exact: false })).toBeVisible();
    const toSeller = page.getByRole('link', { name: '판매자에게 문의' });
    await expect(toSeller).toHaveAttribute('href', `/messages/${shipping.sellerId}`);

    // 판매자가 보면 구매자에게 문의 (같은 두 사람의 방이라 같은 대화방으로 들어간다)
    if (shipping.sellerId === sellerId) {
      await sellerPage.goto(`/orders/${shipping.orderId}`);
      const toBuyer = sellerPage.getByRole('link', { name: '구매자에게 문의' });
      await expect(toBuyer).toHaveAttribute('href', `/messages/${shipping.buyerId}`);
      await expect(sellerPage.getByText('발송 후 7일이 지나면 자동으로 구매 확정돼요', { exact: false })).toBeVisible();
      await toBuyer.click();
      await expect(sellerPage.getByLabel('메시지 입력')).toBeVisible();
    }

    await toSeller.click();
    await expect(page.getByLabel('메시지 입력')).toBeVisible();
  });
});

async function openAs(browser: Browser, account: { email: string; password: string }): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/login');
  await page.getByLabel('이메일').fill(account.email);
  await page.getByLabel('비밀번호').fill(account.password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
  return page;
}

/** 화면이 로그인하며 받은 세션으로 Access Token을 다시 받는다. (같은 계정으로 또 로그인하면 화면의 세션이 끊긴다) */
async function accessTokenOf(page: Page): Promise<Record<string, string>> {
  const res = await page.request.post('/api/v1/auth/reissue');
  const token = ((await res.json()) as { data?: { accessToken?: string } }).data?.accessToken;
  expect(token, 'Access Token을 받지 못했습니다.').toBeTruthy();
  return { Authorization: `Bearer ${token}` };
}

async function buyerOrders(request: APIRequestContext, auth: Record<string, string>): Promise<Order[]> {
  const list = (await (await request.get('/api/v1/orders?role=BUYER&size=50', { headers: auth })).json()) as { data?: { content?: { orderId: number }[] } };
  const details: Order[] = [];
  for (const { orderId } of list.data?.content ?? []) {
    const detail = (await (await request.get(`/api/v1/orders/${orderId}`, { headers: auth })).json()) as { data?: Order };
    if (detail.data) details.push(detail.data);
  }
  return details;
}
