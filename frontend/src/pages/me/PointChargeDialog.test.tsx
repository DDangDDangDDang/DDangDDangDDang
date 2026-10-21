import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PointChargeDialog } from './PointChargeDialog';

type Request = { method: string; path: string; body?: unknown };

// api.POST는 요청 내용만 돌려주고, call()이 경로별로 정해 둔 응답을 준다.
const server = vi.hoisted(() => ({
  requests: [] as Request[],
  chargeAmount: 10000,
  complete: {} as Record<string, unknown>,
  completeFailures: 0,
  completeErrorCode: 'POINT_032',
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (method: string) => (path: string, init?: { body?: unknown }) => ({ method, path, body: init?.body });
  return {
    ...original,
    api: { GET: request('GET'), POST: request('POST') },
    call: async (r: Request) => {
      server.requests.push(r);
      if (r.path === '/api/v1/points/charges') return { chargeId: 1, paymentId: 'point-charge-1', amount: server.chargeAmount, orderName: '10,000 포인트 충전' };
      if (r.path === '/api/v1/points/charges/complete') {
        if (server.completeFailures > 0) {
          server.completeFailures -= 1;
          throw new original.ApiError(server.completeErrorCode, '결제사와 통신 중 오류가 발생했습니다.', 502);
        }
        return server.complete;
      }
      return undefined;
    },
  };
});
const portOne = vi.hoisted(() => ({ requestPayment: vi.fn() }));
vi.mock('@portone/browser-sdk/v2', () => portOne);

const paid = { transactionType: 'PAYMENT', txId: 'tx-1', paymentId: 'point-charge-1' };

function renderDialog() {
  const queryClient = new QueryClient();
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  render(
    <QueryClientProvider client={queryClient}>
      <PointChargeDialog open onClose={() => {}} />
    </QueryClientProvider>,
  );
  return { invalidate };
}

async function payTenThousand() {
  await userEvent.click(screen.getByRole('button', { name: '10,000원' }));
  await userEvent.click(screen.getByRole('button', { name: '10,000원 결제하기' }));
}

describe('PointChargeDialog', () => {
  beforeEach(() => {
    server.requests = [];
    server.complete = { paymentId: 'point-charge-1', amount: 10000, status: 'COMPLETE', balance: 15000 };
    server.completeFailures = 0;
    server.completeErrorCode = 'POINT_032';
    server.chargeAmount = 10000;
    portOne.requestPayment.mockReset();
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
    vi.stubEnv('VITE_PORTONE_STORE_ID', 'store-test');
    vi.stubEnv('VITE_PORTONE_CHANNEL_KEY', 'channel-key-test');
  });
  afterEach(() => vi.unstubAllEnvs());

  it('충전 요청 → 결제창 → 결제 확인 순서로 진행하고, 충전되면 잔액·내역을 새로 불러온다', async () => {
    portOne.requestPayment.mockResolvedValue(paid);
    const { invalidate } = renderDialog();
    await payTenThousand();

    expect(await screen.findByText(/10,000 P를 충전했어요/)).toBeInTheDocument();
    expect(screen.getByText(/사용 가능 포인트는 15,000 P예요/)).toBeInTheDocument();
    expect(portOne.requestPayment).toHaveBeenCalledWith(
      expect.objectContaining({
        storeId: 'store-test',
        channelKey: 'channel-key-test',
        paymentId: 'point-charge-1',
        orderName: '10,000 포인트 충전',
        totalAmount: 10000,
        currency: 'KRW',
        payMethod: 'CARD',
      }),
    );
    expect(server.requests.map((r) => r.path)).toEqual(['/api/v1/points/charges', '/api/v1/points/charges/complete']);
    expect(server.requests[0]?.body).toEqual({ amount: 10000 });
    expect(server.requests[1]?.body).toEqual({ paymentId: 'point-charge-1' });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['points'] });
  });

  it('결제창이 code를 주면(취소·실패) 결제 확인을 부르지 않고 이유를 보여준다', async () => {
    portOne.requestPayment.mockResolvedValue({ ...paid, code: 'FAILURE_TYPE_PG', message: '사용자가 결제를 취소했습니다.' });
    renderDialog();
    await payTenThousand();

    expect(await screen.findByText('사용자가 결제를 취소했습니다.')).toBeInTheDocument();
    expect(server.requests.some((r) => r.path === '/api/v1/points/charges/complete')).toBe(false);
  });

  it('결제 확인이 진행 중(PENDING)이면 새 결제를 막고, 같은 결제의 확인만 다시 할 수 있다', async () => {
    portOne.requestPayment.mockResolvedValue(paid);
    server.complete = { paymentId: 'point-charge-1', amount: 10000, status: 'PENDING' };
    const { invalidate } = renderDialog();
    await payTenThousand();

    expect(await screen.findByText(/결제를 확인하고 있어요. 잠시 후/)).toBeInTheDocument();
    expect(invalidate).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /결제하기/ })).not.toBeInTheDocument();

    server.complete = { paymentId: 'point-charge-1', amount: 10000, status: 'COMPLETE', balance: 15000 };
    await userEvent.click(screen.getByRole('button', { name: '결제 확인 다시 하기' }));
    expect(await screen.findByText(/10,000 P를 충전했어요/)).toBeInTheDocument();
    // 새 충전 요청 없이 같은 paymentId로 다시 확인했다.
    expect(server.requests.filter((r) => r.path === '/api/v1/points/charges')).toHaveLength(1);
    expect(server.requests.filter((r) => r.path === '/api/v1/points/charges/complete').map((r) => r.body)).toEqual([
      { paymentId: 'point-charge-1' },
      { paymentId: 'point-charge-1' },
    ]);
    expect(screen.getByRole('button', { name: '결제하기' })).toBeInTheDocument();
  });

  it('결제 확인 요청이 실패하면 오류를 보여주고, 새 결제 대신 같은 결제의 확인을 다시 하게 한다', async () => {
    portOne.requestPayment.mockResolvedValue(paid);
    server.completeFailures = 1;
    renderDialog();
    await payTenThousand();

    expect(await screen.findByText('결제사와 통신 중 오류가 발생했습니다.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '결제 확인 다시 하기' }));
    expect(await screen.findByText(/10,000 P를 충전했어요/)).toBeInTheDocument();
    expect(portOne.requestPayment).toHaveBeenCalledTimes(1);
    expect(server.requests.filter((r) => r.path === '/api/v1/points/charges')).toHaveLength(1);
  });

  it('결제가 완료되지 않았으면(FAILED) 충전하지 못했다고 안내한다', async () => {
    portOne.requestPayment.mockResolvedValue(paid);
    server.complete = { paymentId: 'point-charge-1', amount: 10000, status: 'FAILED' };
    renderDialog();
    await payTenThousand();

    expect(await screen.findByText(/결제가 완료되지 않아 충전하지 못했어요/)).toBeInTheDocument();
  });

  it('결제창이 예외로 끝나도 그 결제를 붙잡아 두고, 새 결제 대신 확인을 다시 하게 한다', async () => {
    portOne.requestPayment.mockRejectedValue(new Error('popup closed'));
    renderDialog();
    await payTenThousand();

    const recheck = await screen.findByRole('button', { name: '결제 확인 다시 하기' });
    expect(screen.queryByRole('button', { name: /결제하기/ })).not.toBeInTheDocument();
    await userEvent.click(recheck);
    expect(await screen.findByText(/10,000 P를 충전했어요/)).toBeInTheDocument();
    expect(server.requests.filter((r) => r.path === '/api/v1/points/charges')).toHaveLength(1);
  });

  it('붙잡아 둔 결제가 포트원에 없으면(POINT_031) 놓고 새로 충전할 수 있게 한다', async () => {
    portOne.requestPayment.mockRejectedValue(new Error('popup blocked'));
    server.completeFailures = 1;
    server.completeErrorCode = 'POINT_031';
    renderDialog();
    await payTenThousand();

    await userEvent.click(await screen.findByRole('button', { name: '결제 확인 다시 하기' }));
    expect(await screen.findByText('결제가 이루어지지 않아 충전하지 않았어요. 다시 충전해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /결제하기/ })).toBeInTheDocument();
  });

  it('서버가 기록한 충전 금액이 입력과 다르면 결제창을 열지 않는다', async () => {
    server.chargeAmount = 20000;
    renderDialog();
    await payTenThousand();

    expect(await screen.findByText('충전 금액을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.')).toBeInTheDocument();
    expect(portOne.requestPayment).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: '10,000원 결제하기' })).toBeInTheDocument();
  });

  it('범위를 벗어난 금액은 결제창을 열지 않고 입력 아래에 알려준다', async () => {
    renderDialog();
    await userEvent.type(screen.getByLabelText('충전 금액'), '500');
    await userEvent.click(screen.getByRole('button', { name: '500원 결제하기' }));

    expect(screen.getByText('1,000원부터 충전할 수 있어요.')).toBeInTheDocument();
    expect(portOne.requestPayment).not.toHaveBeenCalled();
  });

  it('결제 설정이 없으면 결제창을 띄우지 않고 "충전 설정이 필요합니다"라고 안내한다', async () => {
    vi.stubEnv('VITE_PORTONE_CHANNEL_KEY', '');
    renderDialog();
    expect(screen.getByText(/충전 설정이 필요합니다/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '결제하기' })).toBeDisabled();
    expect(portOne.requestPayment).not.toHaveBeenCalled();
  });
});
