import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BidHistory } from './BidHistory';

type Request = { path: string; init?: { params?: { query?: { cursor?: number } } } };

// 첫 페이지 2건(내 입찰이 최고 입찰), [더 보기]를 누르면 1건 더
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: { GET: (path: string, init: Request['init']) => ({ path, init }) },
    call: async (r: Request) => {
      if (r.init?.params?.query?.cursor === undefined) {
        return {
          content: [
            { bidId: 3, bidderNickname: '땅*이', amount: 21000, status: 'ACTIVE', mine: true, createdAt: '2026-10-26T10:00:00' },
            { bidId: 2, bidderNickname: '경***씨', amount: 20000, status: 'OUTBID', mine: false, createdAt: '2026-10-26T09:59:00' },
          ],
          nextCursor: 2,
          hasNext: true,
        };
      }
      return { content: [{ bidId: 1, bidderNickname: '철*', amount: 15000, status: 'OUTBID', mine: false, createdAt: '2026-10-26T09:00:00' }], hasNext: false };
    },
  };
});

function renderHistory() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <BidHistory auctionId={1} bidCount={3} />
    </QueryClientProvider>,
  );
}

describe('BidHistory', () => {
  it('가린 닉네임과 금액을 최신순으로 보여주고, 내 입찰과 최고 입찰을 표시한다', async () => {
    renderHistory();
    const list = await screen.findByRole('list', { name: '입찰 기록' });
    const [first, second, ...rest] = within(list).getAllByRole('listitem');
    expect(rest).toHaveLength(0);
    if (!first || !second) throw new Error('입찰 기록이 2건이어야 한다');
    expect(within(first).getByText('땅*이')).toBeInTheDocument();
    expect(within(first).getByText('나')).toBeInTheDocument();
    expect(within(first).getByText('최고 입찰')).toBeInTheDocument();
    expect(within(second).queryByText('나')).not.toBeInTheDocument();
  });

  it('[이전 입찰 더 보기]로 다음 페이지를 이어 붙인다', async () => {
    renderHistory();
    await userEvent.click(await screen.findByRole('button', { name: '이전 입찰 더 보기' }));
    expect(await screen.findByText('철*')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '이전 입찰 더 보기' })).not.toBeInTheDocument();
  });
});
