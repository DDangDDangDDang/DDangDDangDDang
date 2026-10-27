import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { NotificationSettingsPage } from './NotificationSettingsPage';

type Request = { path: string; method: string; init?: { params?: { path?: { category?: string } }; body?: { enabled?: boolean } } };

// 알림 설정 두 줄(방송 시작 켜짐, 경매 취소 꺼짐). PUT은 요청대로 바꾼 목록을 돌려준다.
const state = vi.hoisted(() => ({
  settings: [
    { category: 'LIVE_STARTED', label: '방송 시작', description: '구독한 판매자가 방송을 시작할 때', enabled: true },
    { category: 'AUCTION_CANCELED', label: '경매 취소', description: '알림 받기·입찰한 경매가 취소될 때', enabled: false },
  ],
  puts: [] as { category?: string; enabled?: boolean }[],
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => ({ accessToken: 't', user: { userId: 2 } }),
    api: {
      GET: (path: string) => ({ path, method: 'GET' }),
      PUT: (path: string, init: Request['init']) => ({ path, method: 'PUT', init }),
    },
    call: async (r: Request) => {
      if (r.method === 'PUT') {
        const category = r.init?.params?.path?.category;
        const enabled = r.init?.body?.enabled;
        state.puts.push({ category, enabled });
        state.settings = state.settings.map((s) => (s.category === category ? { ...s, enabled: Boolean(enabled) } : s));
      }
      return state.settings;
    },
  };
});

describe('NotificationSettingsPage', () => {
  it('알림 종류별 켜짐을 스위치로 보여 주고, 누르면 그 종류만 바꾼다', async () => {
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <NotificationSettingsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    const live = await screen.findByRole('switch', { name: /방송 시작/ });
    const canceled = screen.getByRole('switch', { name: /경매 취소/ });
    expect(live).toHaveAttribute('aria-checked', 'true');
    expect(canceled).toHaveAttribute('aria-checked', 'false');

    await userEvent.click(canceled);
    expect(state.puts).toEqual([{ category: 'AUCTION_CANCELED', enabled: true }]);
    expect(await screen.findByRole('switch', { name: /경매 취소/ })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByText('알림함의 알림은 90일 동안 보관하고, 그 뒤에는 자동으로 지워요.')).toBeInTheDocument();
  });
});
