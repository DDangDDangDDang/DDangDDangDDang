import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ModerationAlert, Schema } from '@/api';
import { ModerationPanel } from './ModerationPanel';

type Request = { path: string; method: string; init?: { params?: { path?: Record<string, unknown> }; body?: Record<string, unknown> } };

const state = vi.hoisted(() => ({ requests: [] as Request[] }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: {
      PATCH: (path: string, init: Request['init']) => ({ path, method: 'PATCH', init }),
      POST: (path: string, init: Request['init']) => ({ path, method: 'POST', init }),
    },
    call: async (r: Request) => {
      state.requests.push(r);
      if (r.method === 'PATCH') return { id: 1, chatModerationMode: r.init?.body?.chatModerationMode };
      return undefined;
    },
  };
});

const liveStream = { id: 1, sellerId: 9, chatModerationMode: 'ALERT' } as Schema<'LiveStreamResponse'> & { id: number };

const alert: ModerationAlert = {
  liveStreamId: 1,
  messageId: 'm-1',
  messageType: 'CHAT',
  senderId: 2,
  nickname: '사진찍는곰',
  content: '카톡으로 따로 거래해요',
  category: 'EXTERNAL_TRADE',
  categoryLabel: '외부 거래 유도',
  reason: '플랫폼 밖 거래를 유도함',
  autoHidden: false,
  detectedAt: '2026-10-21T20:00:00',
};

function renderPanel(alerts: ModerationAlert[], props: { onDismiss?: (id: string) => void; onSaved?: () => void; mode?: string } = {}) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { mutations: { retry: false } } })}>
      <ModerationPanel
        liveStream={{ ...liveStream, chatModerationMode: (props.mode ?? 'ALERT') as typeof liveStream.chatModerationMode }}
        alerts={alerts}
        onDismiss={props.onDismiss ?? (() => {})}
        onSaved={props.onSaved ?? (() => {})}
      />
    </QueryClientProvider>,
  );
}

describe('ModerationPanel', () => {
  beforeEach(() => {
    state.requests = [];
  });

  it('알림이 없으면 어떤 채팅이 여기에 오는지 안내하고, AI 검토를 끈 방송이면 끈 상태를 알린다', () => {
    renderPanel([]);
    expect(screen.getByText(/외부 거래 유도, 욕설 같은 채팅이 오면/)).toBeInTheDocument();
  });

  it('AI 검토를 끈 방송이면 기본 규칙만 적용한다고 알린다', () => {
    renderPanel([], { mode: 'OFF' });
    expect(screen.getByText(/AI 검토를 껐어요/)).toBeInTheDocument();
  });

  it('분류 이름·보낸 사람·내용·판단 이유를 보여주고, [확인]을 누르면 그 알림을 지운다', async () => {
    const onDismiss = vi.fn();
    renderPanel([alert], { onDismiss });

    const list = screen.getByRole('list', { name: '검토가 필요한 채팅' });
    expect(list).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByText('외부 거래 유도')).toBeInTheDocument();
    expect(screen.getByText('사진찍는곰')).toBeInTheDocument();
    expect(screen.getByText(/카톡으로 따로 거래해요/)).toBeInTheDocument();
    expect(screen.getByText('플랫폼 밖 거래를 유도함')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /되돌리기/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '사진찍는곰님 채팅 알림 확인' }));
    expect(onDismiss).toHaveBeenCalledWith('m-1');
  });

  it('처리 방식을 바꾸면 방송 정보를 저장하고 저장된 방송을 넘긴다', async () => {
    const onSaved = vi.fn();
    renderPanel([], { onSaved });

    await userEvent.selectOptions(screen.getByLabelText('AI가 문제 채팅을 찾으면'), 'AUTO_HIDE');

    expect(state.requests[0]).toMatchObject({ method: 'PATCH', path: '/api/v1/live-streams/{liveStreamId}', init: { body: { chatModerationMode: 'AUTO_HIDE' } } });
    expect(onSaved).toHaveBeenCalledWith({ id: 1, chatModerationMode: 'AUTO_HIDE' });
  });

  it('자동으로 가린 메시지는 [되돌리기]로 다시 보이게 하고, 선물 한마디는 따로 표시한다', async () => {
    renderPanel([{ ...alert, messageType: 'DONATION', autoHidden: true }]);

    expect(screen.getByText('선물 한마디')).toBeInTheDocument();
    expect(screen.getByText('자동으로 가렸어요')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '사진찍는곰님 메시지 되돌리기' }));

    expect(state.requests[0]).toMatchObject({
      method: 'POST',
      path: '/api/v1/live-streams/{liveStreamId}/chat/messages/{messageId}/restore',
      init: { params: { path: { liveStreamId: 1, messageId: 'm-1' } } },
    });
    expect(await screen.findByText('시청자에게 다시 보여요')).toBeInTheDocument();
  });
});
