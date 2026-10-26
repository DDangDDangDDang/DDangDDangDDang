import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, type Schema } from '@/api';
import { TrackingCard } from './TrackingCard';

// 배송 조회 응답을 테스트마다 정한다
const server = vi.hoisted(() => ({ tracking: (): unknown => undefined }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: { GET: (path: string) => ({ path }) },
    call: async () => server.tracking(),
  };
});

const shipped: Schema<'OrderResponse'> = { orderId: 1, courier: 'CJ', trackingNo: '1234567890', status: 'SHIPPING' };

function renderCard(order = shipped) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <TrackingCard order={order} />
    </QueryClientProvider>,
  );
}

describe('TrackingCard', () => {
  beforeEach(() => {
    server.tracking = () => undefined;
  });

  it('현재 상태 배지와 이력을 최신 순으로 보여주고, 택배사 조회 페이지 링크를 단다', async () => {
    server.tracking = () => ({
      courier: 'CJ',
      trackingNo: '1234567890',
      lastEvent: { time: '2026-10-26T18:10:00+09:00', statusCode: 'OUT_FOR_DELIVERY', statusName: '배송출발', location: '강남대리점' },
      events: [
        { time: '2026-10-25T20:00:00+09:00', statusCode: 'AT_PICKUP', statusName: '집화처리', location: '곤지암Hub' },
        { time: '2026-10-26T18:10:00+09:00', statusCode: 'OUT_FOR_DELIVERY', statusName: '배송출발', location: '강남대리점' },
      ],
    });
    renderCard();

    expect(await screen.findByText('배달 출발')).toBeInTheDocument();
    const items = within(screen.getByRole('list', { name: '배송 이력' })).getAllByRole('listitem');
    expect(items[0]).toHaveTextContent('배송출발');
    expect(items[1]).toHaveTextContent('집화처리');
    expect(screen.getByRole('link', { name: /CJ대한통운에서 보기/ })).toHaveAttribute(
      'href',
      'https://trace.cjlogistics.com/next/tracking.html?wblNo=1234567890',
    );
  });

  it('단계 그림: 지금 단계를 표시하고 단계 아래에 택배사 상태 이름을 적는다', async () => {
    server.tracking = () => ({
      courier: 'CJ',
      trackingNo: '1234567890',
      lastEvent: { time: '2026-10-26T09:00:00+09:00', statusCode: 'IN_TRANSIT', statusName: '간선상차', location: '곤지암Hub' },
      events: [
        { time: '2026-10-25T10:00:00+09:00', statusCode: 'INFORMATION_RECEIVED', statusName: '접수' },
        { time: '2026-10-25T20:00:00+09:00', statusCode: 'AT_PICKUP', statusName: '집화처리' },
        { time: '2026-10-26T03:00:00+09:00', statusCode: 'IN_TRANSIT', statusName: '간선하차' },
        { time: '2026-10-26T09:00:00+09:00', statusCode: 'IN_TRANSIT', statusName: '간선상차' },
      ],
    });
    renderCard();

    const steps = within(await screen.findByRole('list', { name: '배송 단계' })).getAllByRole('listitem');
    expect(steps.map((step) => step.getAttribute('aria-current'))).toEqual([null, null, 'step', null]);
    expect(steps[2]).toHaveTextContent('배송 중');
    expect(steps[2]).toHaveTextContent('간선하차 · 간선상차');
    expect(steps[3]).toHaveTextContent('도착');
  });

  it('이력 없이 현재 상태만 오면 단계 그림을 현재 상태로 그린다', async () => {
    server.tracking = () => ({
      courier: 'CJ',
      trackingNo: '1234567890',
      lastEvent: { time: '2026-10-26T09:00:00+09:00', statusCode: 'OUT_FOR_DELIVERY', statusName: '배송출발', location: '강남대리점' },
      events: [],
    });
    renderCard();

    const steps = within(await screen.findByRole('list', { name: '배송 단계' })).getAllByRole('listitem');
    expect(steps[2]).toHaveAttribute('aria-current', 'step');
    expect(steps[2]).toHaveTextContent('배송출발');
    expect(screen.queryByText(/최근 이력 보기/)).not.toBeInTheDocument();
  });

  it('배달 완료 뒤에 이벤트가 더 와도 도착 단계에 배달완료를 한 번만 적는다', async () => {
    const delivered = { time: '2026-10-26T15:00:00+09:00', statusCode: 'DELIVERED', statusName: '배달완료', location: '강남' };
    server.tracking = () => ({
      courier: 'CJ',
      trackingNo: '1234567890',
      lastEvent: delivered,
      events: [
        { time: '2026-10-25T20:00:00+09:00', statusCode: 'AT_PICKUP', statusName: '집화처리' },
        delivered,
        { time: '2026-10-26T18:00:00+09:00', statusCode: 'UNKNOWN', statusName: '반품접수' },
      ],
    });
    renderCard();

    const steps = within(await screen.findByRole('list', { name: '배송 단계' })).getAllByRole('listitem');
    expect(steps[3]).toHaveAttribute('aria-current', 'step');
    expect(steps[3]).toHaveTextContent(/^도착 \(현재\)배달완료 · 반품접수$/);
  });

  it('아직 택배사에 등록 전이면(ORDER_014) 안내하고 택배사 사이트를 권한다', async () => {
    server.tracking = () => {
      throw new ApiError('ORDER_014', '아직 조회되는 배송 정보가 없습니다. 잠시 후 다시 확인해 주세요.', 404);
    };
    renderCard();

    expect(await screen.findByText(/아직 조회되는 배송 정보가 없습니다/)).toBeInTheDocument();
    expect(screen.getByText(/택배사 사이트에서 직접 확인할 수도 있어요/)).toBeInTheDocument();
  });

  it('지원하지 않는 택배사면(ORDER_013) 송장번호로 문의하라고 안내한다', async () => {
    server.tracking = () => {
      throw new ApiError('ORDER_013', '배송 조회를 지원하지 않는 택배사입니다.', 400);
    };
    renderCard({ ...shipped, courier: 'ETC' });

    expect(await screen.findByText(/이 택배사는 배송 조회를 지원하지 않아요/)).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('송장번호가 없으면(발송 전) 아무것도 그리지 않는다', () => {
    renderCard({ ...shipped, trackingNo: undefined });

    expect(screen.queryByText('배송 조회')).not.toBeInTheDocument();
  });
});
