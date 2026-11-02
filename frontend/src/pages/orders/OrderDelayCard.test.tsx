import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/api';
import { OrderDelayCard } from './OrderDelayCard';

type Request = { path: string; init?: { body?: { reason?: string } } };

const state = vi.hoisted(() => ({ requests: [] as Request[] }));

vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (path: string, init: Request['init']) => ({ path, init });
  return {
    ...original,
    api: { POST: request },
    call: async (r: Request) => {
      state.requests.push(r);
      return { orderId: 10 };
    },
  };
});

const waiting: Schema<'OrderResponse'> = {
  orderId: 10,
  status: 'ESCROW',
  receiverName: '홍길동',
  receiverPhone: '01012345678',
  address: '서울',
};

function renderCard(order: Schema<'OrderResponse'>, role: 'BUYER' | 'SELLER') {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <OrderDelayCard order={order} role={role} />
    </QueryClientProvider>,
  );
}

describe('발송 지연 카드', () => {
  beforeEach(() => {
    state.requests = [];
  });

  it('판매자는 사유를 적어 지연을 신청한다', async () => {
    renderCard(waiting, 'SELLER');
    expect(screen.getByRole('button', { name: '지연 신청' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('지연 사유'), '입고 지연');
    await userEvent.click(screen.getByRole('button', { name: '지연 신청' }));

    expect(state.requests[0]?.path).toBe('/api/v1/orders/{orderId}/delay-request');
    expect(state.requests[0]?.init?.body?.reason).toBe('입고 지연');
  });

  it('구매자는 대기 중인 신청의 사유를 보고 승인하거나 거절한다', async () => {
    renderCard({ ...waiting, delayStatus: 'PENDING', delayReason: '입고 지연' }, 'BUYER');
    expect(screen.getByText(/입고 지연/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '거절' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '승인' }));

    expect(state.requests[0]?.path).toBe('/api/v1/orders/{orderId}/delay-request/approve');
  });

  it('판매자는 대기 중이면 버튼 없이 응답을 기다린다', () => {
    renderCard({ ...waiting, delayStatus: 'PENDING', delayReason: '입고 지연' }, 'SELLER');

    expect(screen.getByText('구매자 응답 대기 중')).toBeInTheDocument();
    expect(screen.queryByRole('button')).toBeNull();
  });

  it.each(['APPROVED', 'REJECTED'] as const)('%s이면 다시 신청할 수 없다', (delayStatus) => {
    renderCard({ ...waiting, delayStatus }, 'SELLER');

    expect(screen.queryByRole('button')).toBeNull();
  });

  it('구매자는 신청 전에는 카드를 보지 않는다', () => {
    const { container } = renderCard(waiting, 'BUYER');

    expect(container).toBeEmptyDOMElement();
  });

  it.each(['SHIPPING', 'CANCELED'] as const)('%s 주문에는 카드가 없다', (status) => {
    const { container } = renderCard({ ...waiting, status }, 'SELLER');

    expect(container).toBeEmptyDOMElement();
  });
});
