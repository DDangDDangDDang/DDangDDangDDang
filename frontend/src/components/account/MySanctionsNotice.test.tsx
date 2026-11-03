import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MySanctionsNotice } from './MySanctionsNotice';

const state = vi.hoisted(() => ({ sanctions: [] as { type: string; bannedUntil: string; cancelCount: number }[] }));

vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: { GET: (path: string) => ({ path }) },
    call: async () => state.sanctions,
  };
});

function renderNotice() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MySanctionsNotice />
    </QueryClientProvider>,
  );
}

describe('내 정지 안내', () => {
  beforeEach(() => {
    state.sanctions = [];
  });

  it('정지가 없으면 아무것도 보여 주지 않는다', async () => {
    const { container } = renderNotice();

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(container).toBeEmptyDOMElement();
  });

  it('정지 종류, 이유, 풀리는 시각을 알려 준다', async () => {
    state.sanctions = [{ type: 'BID_BAN', bannedUntil: '2026-11-10T14:00:00', cancelCount: 2 }];
    renderNotice();

    const notice = await screen.findByRole('alert');
    expect(notice).toHaveTextContent('입찰·즉시 구매가 정지됐어요');
    expect(notice).toHaveTextContent('배송지 미입력 낙찰취소 2회');
    expect(notice).toHaveTextContent('11월 10일');
  });

  it('경매 등록 정지도 따로 알려 준다', async () => {
    state.sanctions = [{ type: 'LISTING_BAN', bannedUntil: '2026-12-01T09:00:00', cancelCount: 3 }];
    renderNotice();

    const notice = await screen.findByRole('alert');
    expect(notice).toHaveTextContent('경매 등록이 정지됐어요');
    expect(notice).toHaveTextContent('미발송 판매 취소 3회');
  });
});
