import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { LiveChat } from './LiveChat';

vi.mock('@/api', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/api')>()), useSession: () => null }));

function Chat({ notice }: { notice?: string }) {
  return <LiveChat messages={[]} canChat status="connected" onSend={() => true} notice={notice} />;
}

describe('LiveChat 고정 공지', () => {
  it('판매자 공지를 채팅창 위에 보여주고, [X]를 누르면 닫는다', async () => {
    render(<Chat notice="오늘 9시에 한정판 경매가 있어요" />);
    expect(screen.getByRole('note', { name: '판매자 공지' })).toHaveTextContent('오늘 9시에 한정판 경매가 있어요');

    await userEvent.click(screen.getByRole('button', { name: '공지 닫기' }));
    expect(screen.queryByRole('note', { name: '판매자 공지' })).not.toBeInTheDocument();
  });

  it('닫은 뒤 판매자가 공지를 바꾸면 새 공지는 보인다', async () => {
    const { rerender } = render(<Chat notice="첫 공지" />);
    await userEvent.click(screen.getByRole('button', { name: '공지 닫기' }));

    rerender(<Chat notice="바뀐 공지" />);
    expect(screen.getByRole('note', { name: '판매자 공지' })).toHaveTextContent('바뀐 공지');
  });

  it('판매자가 공지를 내렸다가 같은 문구로 다시 올리면 다시 보인다', async () => {
    const { rerender } = render(<Chat notice="같은 공지" />);
    await userEvent.click(screen.getByRole('button', { name: '공지 닫기' }));

    rerender(<Chat />);
    rerender(<Chat notice="같은 공지" />);
    expect(screen.getByRole('note', { name: '판매자 공지' })).toHaveTextContent('같은 공지');
  });

  it('닫아도 다시 들어오면(새로 그리면) 다시 보인다', async () => {
    const { unmount } = render(<Chat notice="공지" />);
    await userEvent.click(screen.getByRole('button', { name: '공지 닫기' }));
    unmount();

    render(<Chat notice="공지" />);
    expect(screen.getByRole('note', { name: '판매자 공지' })).toBeInTheDocument();
  });

  it('공지가 없으면 아무것도 그리지 않는다', () => {
    render(<Chat />);
    expect(screen.queryByRole('note', { name: '판매자 공지' })).not.toBeInTheDocument();
  });
});

describe('LiveChat 도네이션 선물', () => {
  it('선물 메시지는 "누구님이 몇 M을 선물했어요"로 한 줄 남기고, 한마디가 있으면 함께 보여 준다', () => {
    const base = { liveStreamId: 1, senderId: 3, nickname: '필름팬', sentAt: '2026-10-23T12:00:00' };
    render(
      <LiveChat
        messages={[
          { ...base, messageId: 'a', type: 'DONATION', amount: 1000, content: '오늘 방송 최고예요' },
          { ...base, messageId: 'b', type: 'DONATION', amount: 300, content: null },
        ]}
        canChat
        status="connected"
        onSend={() => true}
      />,
    );
    const [withMessage, plain] = screen.getAllByRole('listitem');
    expect(withMessage).toHaveTextContent('필름팬님이 1,000 M을 선물했어요 · 오늘 방송 최고예요');
    expect(plain).toHaveTextContent(/^필름팬님이 300 M을 선물했어요$/);
  });
});
