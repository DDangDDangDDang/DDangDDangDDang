import { expect, test, type Browser, type Page } from '@playwright/test';

/*
 * 1:1 채팅: 시청자가 판매자에게 문의 → 판매자가 실시간으로 받고 읽음("1" 사라짐) → 답장 → 신고 → 차단·해제
 *
 * 로컬 백엔드가 떠 있어야 한다. 시청자(E2E_EMAIL)·판매자(E2E_SELLER_EMAIL) 두 계정이 필요하다. (frontend/README.md "E2E 테스트")
 */

const viewer = { email: process.env.E2E_EMAIL ?? '', password: process.env.E2E_PASSWORD ?? '' };
const seller = { email: process.env.E2E_SELLER_EMAIL ?? '', password: process.env.E2E_SELLER_PASSWORD ?? '' };

test.describe('1:1 채팅', () => {
  test.skip(!viewer.email || !seller.email || viewer.email === seller.email, '시청자와 판매자 계정이 따로 필요합니다. (E2E_EMAIL, E2E_SELLER_EMAIL)');

  test('문의를 보내면 상대가 실시간으로 받고, 읽으면 "1"이 사라지고, 답장이 온다', async ({ browser, request }) => {
    // 판매자 회원 번호 (화면에서 로그인하기 전에 API로 알아 둔다)
    const login = await request.post('/api/v1/auth/login', { data: seller });
    const token = ((await login.json()) as { data?: { accessToken?: string } }).data?.accessToken;
    const me = await request.get('/api/v1/users/me', { headers: { Authorization: `Bearer ${token}` } });
    const sellerId = ((await me.json()) as { data?: { userId?: number } }).data?.userId;
    expect(sellerId, '판매자 정보를 불러오지 못했습니다.').toBeTruthy();

    const viewerPage = await openAs(browser, viewer);
    const sellerPage = await openAs(browser, seller);

    try {
      // 판매자는 메시지 화면을 열어 둔다.
      await sellerPage.goto('/messages');
      await expect(sellerPage.getByRole('heading', { name: '메시지', level: 1 })).toBeVisible();

      // 시청자가 판매자에게 보낸다.
      await viewerPage.goto(`/messages/${sellerId}`);
      const question = `E2E 문의 ${Date.now()}`;
      await viewerPage.getByLabel('메시지 입력').fill(question);
      await viewerPage.getByLabel('메시지 입력').press('Enter');
      const viewerList = viewerPage.getByRole('list', { name: /님과의 메시지/ });
      await expect(viewerList.getByText(question)).toBeVisible();
      await expect(viewerList.getByLabel('상대가 아직 읽지 않음').last()).toBeVisible();

      // 판매자: 대화방 목록에 실시간으로 나타나고, 열면 메시지가 보인다.
      const room = sellerPage.getByRole('link', { name: new RegExp(question) });
      await expect(room).toBeVisible();
      await room.click();
      const sellerList = sellerPage.getByRole('list', { name: /님과의 메시지/ });
      await expect(sellerList.getByText(question)).toBeVisible();

      // 판매자가 읽었으니 시청자 화면의 "1"이 사라진다.
      await expect(viewerList.getByLabel('상대가 아직 읽지 않음')).toHaveCount(0);

      // 판매자 답장이 시청자에게 실시간으로 온다.
      const answer = `E2E 답장 ${Date.now()}`;
      await sellerPage.getByLabel('메시지 입력').fill(answer);
      await sellerPage.getByRole('button', { name: '메시지 보내기' }).click();
      await expect(viewerList.getByText(answer)).toBeVisible();

      // 시청자가 답장을 신고한다.
      await viewerList.getByText(answer).hover();
      await viewerPage.getByRole('button', { name: '이 메시지 신고' }).last().click();
      const reportDialog = viewerPage.getByRole('dialog');
      await reportDialog.getByLabel('신고 이유').selectOption('SPAM');
      await reportDialog.getByRole('button', { name: '신고', exact: true }).click();
      await expect(viewerPage.getByRole('dialog', { name: '신고를 접수했어요' })).toBeVisible();
      await viewerPage.getByRole('dialog', { name: '신고를 접수했어요' }).getByRole('button', { name: '확인' }).click();

      // 차단하면 입력창 대신 안내가 나오고, 해제하면 다시 보낼 수 있다.
      await viewerPage.getByRole('button', { name: '차단', exact: true }).click();
      await viewerPage.getByRole('dialog').getByRole('button', { name: '차단', exact: true }).click();
      await expect(viewerPage.getByText('차단한 상대예요.')).toBeVisible();
      await viewerPage.getByRole('button', { name: '차단 해제' }).first().click();
      await viewerPage.getByRole('dialog').getByRole('button', { name: '차단 해제' }).click();
      await expect(viewerPage.getByLabel('메시지 입력')).toBeVisible();

      await test.info().attach('시청자 대화 화면', { body: await viewerPage.screenshot(), contentType: 'image/png' });
      await test.info().attach('판매자 메시지 화면', { body: await sellerPage.screenshot(), contentType: 'image/png' });
    } finally {
      await viewerPage.context().close();
      await sellerPage.context().close();
    }
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
