import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { LiveListPage } from './LiveListPage';

type Request = { path: string; params?: { query?: { status?: string } } };

// api.GET은 요청 내용만 돌려주고, call()이 요청한 방송 상태를 기록한 뒤 빈 목록을 준다.
const server = vi.hoisted(() => ({ statuses: [] as (string | undefined)[] }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: { GET: (path: string, init?: Request) => ({ path, params: init?.params }) },
    call: async (r: Request) => {
      server.statuses.push(r.params?.query?.status);
      return { content: [], hasNext: false };
    },
  };
});

function renderPage(url: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[url]}>
        <LiveListPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('LiveListPage', () => {
  beforeEach(() => {
    server.statuses = [];
  });

  it('방송 중·방송 예정 탭만 보여 준다', async () => {
    renderPage('/lives');

    expect(await screen.findByText('지금 라이브 중인 방송이 없어요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '방송 중' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: '방송 예정' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '지난 방송' })).not.toBeInTheDocument();
  });

  it('예전 지난 방송 주소로 들어오면 방송 중 탭을 보여 준다', async () => {
    renderPage('/lives?status=ENDED');

    expect(await screen.findByText('지금 라이브 중인 방송이 없어요')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '방송 중' })).toHaveAttribute('aria-pressed', 'true');
    expect(server.statuses).toEqual(['LIVE']);
  });
});
