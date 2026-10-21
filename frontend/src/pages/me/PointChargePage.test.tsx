import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PointChargePage } from './PointChargePage';

type Request = { method: string; path: string; body?: unknown };

// api.GET/POST는 요청 내용만 돌려주고, call()이 경로별로 정해 둔 응답을 준다.
const server = vi.hoisted(() => ({ requests: [] as Request[], respond: (() => undefined) as (r: Request) => unknown }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (method: string) => (path: string, init?: { body?: unknown }) => ({ method, path, body: init?.body });
  return {
    ...original,
    api: { GET: request('GET'), POST: request('POST') },
    call: async (r: Request) => {
      server.requests.push(r);
      return server.respond(r);
    },
  };
});
const portOne = vi.hoisted(() => ({ requestPayment: vi.fn() }));
vi.mock('@portone/browser-sdk/v2', () => portOne);

function renderPage(path = '/points/charge') {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[path]}>
        <PointChargePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('PointChargePage', () => {
  beforeEach(() => {
    server.requests = [];
    portOne.requestPayment.mockReset();
    vi.stubEnv('VITE_PORTONE_STORE_ID', 'store-test');
    vi.stubEnv('VITE_PORTONE_CHANNEL_KEY', 'channel-key-test');
    server.respond = (r) => {
      if (r.path === '/api/v1/points/wallet') return { balance: 5000 };
      if (r.path === '/api/v1/points/charges') return { chargeId: 1, paymentId: 'pay-1', amount: 30000, orderName: '30,000 포인트 충전' };
      if (r.path === '/api/v1/points/charges/complete') return { paymentId: 'pay-1', amount: 30000, status: 'COMPLETE', balance: 35000 };
      return undefined;
    };
  });
  afterEach(() => vi.unstubAllEnvs());

  it('금액을 고르고 결제하면 결제창을 연 뒤 서버에 결제 확인을 요청하고 충전 결과를 보여준다', async () => {
    portOne.requestPayment.mockResolvedValue({ transactionType: 'PAYMENT', txId: 'tx-1', paymentId: 'pay-1' });
    renderPage();
    expect(await screen.findByText('5,000 P')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '30,000원' }));
    await userEvent.click(screen.getByRole('button', { name: '30,000원 결제하기' }));

    expect(await screen.findByText(/30,000 P를 충전했어요/)).toBeInTheDocument();
    expect(screen.getByText(/사용 가능 포인트는 35,000 P예요/)).toBeInTheDocument();
    expect(portOne.requestPayment).toHaveBeenCalledWith(
      expect.objectContaining({ storeId: 'store-test', channelKey: 'channel-key-test', paymentId: 'pay-1', totalAmount: 30000, currency: 'KRW', payMethod: 'CARD' }),
    );
    expect(server.requests.find((r) => r.path === '/api/v1/points/charges')?.body).toEqual({ amount: 30000 });
    expect(server.requests.find((r) => r.path === '/api/v1/points/charges/complete')?.body).toEqual({ paymentId: 'pay-1' });
  });

  it('결제창을 닫으면 결제 확인을 요청하지 않고 이유를 보여준다', async () => {
    portOne.requestPayment.mockResolvedValue({ transactionType: 'PAYMENT', txId: 'tx-1', paymentId: 'pay-1', code: 'FAILURE_TYPE_PG', message: '사용자가 결제를 취소했습니다.' });
    renderPage();

    await userEvent.type(screen.getByLabelText('직접 입력'), '30000');
    await userEvent.click(screen.getByRole('button', { name: '30,000원 결제하기' }));

    expect(await screen.findByText('사용자가 결제를 취소했습니다.')).toBeInTheDocument();
    expect(server.requests.some((r) => r.path === '/api/v1/points/charges/complete')).toBe(false);
  });

  it('범위를 벗어난 금액은 결제창을 열지 않고 입력 아래에 알려준다', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('직접 입력'), '500');
    await userEvent.click(screen.getByRole('button', { name: '500원 결제하기' }));

    expect(screen.getByText('1,000원부터 충전할 수 있어요.')).toBeInTheDocument();
    expect(portOne.requestPayment).not.toHaveBeenCalled();
  });

  it('결제 설정이 없으면 결제 버튼을 막고 설정 방법을 알려준다', async () => {
    vi.stubEnv('VITE_PORTONE_CHANNEL_KEY', '');
    renderPage();
    expect(screen.getByText(/VITE_PORTONE_STORE_ID와 VITE_PORTONE_CHANNEL_KEY/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '결제하기' })).toBeDisabled();
  });

  it('결제창이 이 화면으로 돌아오면 주소의 paymentId로 결제를 확인한다', async () => {
    renderPage('/points/charge?paymentId=pay-1&transactionType=PAYMENT&txId=tx-1');
    expect(await screen.findByText(/30,000 P를 충전했어요/)).toBeInTheDocument();
    expect(server.requests.filter((r) => r.path === '/api/v1/points/charges/complete')).toHaveLength(1);
  });
});
