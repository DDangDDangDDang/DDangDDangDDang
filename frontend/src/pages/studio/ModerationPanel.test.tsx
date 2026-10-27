import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { ModerationAlert } from '@/api';
import { ModerationPanel } from './ModerationPanel';

const alert: ModerationAlert = {
  liveStreamId: 1,
  messageId: 'm-1',
  senderId: 2,
  nickname: '사진찍는곰',
  content: '카톡으로 따로 거래해요',
  category: 'EXTERNAL_TRADE',
  categoryLabel: '외부 거래 유도',
  reason: '플랫폼 밖 거래를 유도함',
  detectedAt: '2026-10-21T20:00:00',
};

describe('ModerationPanel', () => {
  it('알림이 없으면 어떤 채팅이 여기에 오는지 안내한다', () => {
    render(<ModerationPanel alerts={[]} onDismiss={() => {}} />);
    expect(screen.getByText(/외부 거래 유도, 욕설 같은 채팅이 오면/)).toBeInTheDocument();
  });

  it('분류 이름·보낸 사람·내용·판단 이유를 보여주고, [확인]을 누르면 그 알림을 지운다', async () => {
    const onDismiss = vi.fn();
    render(<ModerationPanel alerts={[alert]} onDismiss={onDismiss} />);

    const list = screen.getByRole('list', { name: '검토가 필요한 채팅' });
    expect(list).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByText('외부 거래 유도')).toBeInTheDocument();
    expect(screen.getByText('사진찍는곰')).toBeInTheDocument();
    expect(screen.getByText(/카톡으로 따로 거래해요/)).toBeInTheDocument();
    expect(screen.getByText('플랫폼 밖 거래를 유도함')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '사진찍는곰님 채팅 알림 확인' }));
    expect(onDismiss).toHaveBeenCalledWith('m-1');
  });
});
