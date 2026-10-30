import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MiniPlayer } from './MiniPlayer';
import { useWatchStream } from './watch-context';
import { WatchProvider } from './WatchProvider';

// 방송 연결(LiveKit)은 하지 않고, 어떤 방송에 접속하려 했는지만 기록한다.
const video = vi.hoisted(() => ({ calls: [] as { id: number; enabled: boolean }[], roomClosed: undefined as (() => void) | undefined }));
vi.mock('@/pages/live/use-live-video', () => ({
  useLiveVideo: (id: number, enabled: boolean, onRoomClosed: () => void) => {
    video.calls.push({ id, enabled });
    video.roomClosed = onRoomClosed;
    return { phase: enabled ? 'connected' : 'idle', audioTracks: [], viewerCount: enabled ? 3 : undefined, retry: vi.fn(), startAudio: vi.fn() };
  },
}));
vi.mock('@/components/broadcast/broadcast-context', () => ({ useBroadcast: () => ({ publishing: false }) }));

function WatchPage({ id, live, mine }: { id: number; live: boolean; mine?: boolean }) {
  useWatchStream({ liveStreamId: id, title: `방송 ${id}`, live, mine }, () => undefined);
  return <p>시청 화면 {id}</p>;
}

/** 시청 화면을 열고 닫는 버튼 (화면 이동 대신) */
function Harness() {
  const [page, setPage] = useState<{ id: number; live: boolean; mine?: boolean } | null>({ id: 7, live: true });
  return (
    <WatchProvider>
      {page && <WatchPage id={page.id} live={page.live} mine={page.mine} />}
      <button type="button" onClick={() => setPage(null)}>
        다른 화면으로
      </button>
      <button type="button" onClick={() => setPage({ id: 7, live: true })}>
        7번 방송으로
      </button>
      <button type="button" onClick={() => setPage({ id: 8, live: false })}>
        8번 예정 방송으로
      </button>
      <button type="button" onClick={() => setPage({ id: 9, live: true, mine: true })}>
        내 9번 방송으로
      </button>
      <MiniPlayer />
    </WatchProvider>
  );
}

const enabledIds = () => [...new Set(video.calls.filter((c) => c.enabled).map((c) => c.id))];

describe('시청자 작은 플레이어', () => {
  beforeEach(() => {
    video.calls = [];
    video.roomClosed = undefined;
  });

  it('방송 중에 시청 화면을 떠나면 작은 플레이어에서 이어 보고, 돌아오면 다시 접속하지 않고 작은 플레이어를 숨긴다', async () => {
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    );
    expect(screen.queryByRole('complementary', { name: '보던 방송' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '다른 화면으로' }));
    const mini = screen.getByRole('complementary', { name: '보던 방송' });
    expect(mini).toHaveTextContent('방송 7');
    expect(mini).toHaveTextContent('3명 시청');
    expect(screen.getByRole('link', { name: '방송으로 돌아가기' })).toHaveAttribute('href', '/lives/7');

    await userEvent.click(screen.getByRole('button', { name: '7번 방송으로' }));
    expect(screen.queryByRole('complementary', { name: '보던 방송' })).not.toBeInTheDocument();
    // 처음부터 끝까지 7번 방송 연결 하나만 켜져 있었다. (끊었다 다시 접속하지 않음)
    expect(enabledIds()).toEqual([7]);
    const firstOn = video.calls.findIndex((c) => c.enabled);
    expect(video.calls.slice(firstOn).every((c) => c.enabled)).toBe(true);
  });

  it('[닫기]를 누르면 연결을 끊고, 방송 중이 아닌 방송은 떠나도 작은 플레이어를 띄우지 않는다', async () => {
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole('button', { name: '다른 화면으로' }));
    await userEvent.click(screen.getByRole('button', { name: '작은 플레이어 닫기' }));
    expect(screen.queryByRole('complementary', { name: '보던 방송' })).not.toBeInTheDocument();
    expect(video.calls.at(-1)).toMatchObject({ enabled: false });

    await userEvent.click(screen.getByRole('button', { name: '8번 예정 방송으로' }));
    await userEvent.click(screen.getByRole('button', { name: '다른 화면으로' }));
    expect(screen.queryByRole('complementary', { name: '보던 방송' })).not.toBeInTheDocument();
  });

  it('작은 플레이어로 보던 방송이 끝나면 끝났다고 알려 준다', async () => {
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole('button', { name: '다른 화면으로' }));
    act(() => video.roomClosed?.());
    expect(screen.getByRole('complementary', { name: '보던 방송' })).toHaveTextContent('방송이 끝났어요');
  });

  it('내(판매자) 방송은 시청 화면을 떠나면 연결을 끊고 작은 플레이어를 띄우지 않는다', async () => {
    render(
      <MemoryRouter>
        <Harness />
      </MemoryRouter>,
    );
    await userEvent.click(screen.getByRole('button', { name: '내 9번 방송으로' }));
    expect(enabledIds()).toContain(9);
    await userEvent.click(screen.getByRole('button', { name: '다른 화면으로' }));
    expect(screen.queryByRole('complementary', { name: '보던 방송' })).not.toBeInTheDocument();
    expect(video.calls.at(-1)).toMatchObject({ enabled: false });

    // 다른 사람의 방송은 평소처럼 작은 플레이어에서 이어 본다.
    await userEvent.click(screen.getByRole('button', { name: '7번 방송으로' }));
    await userEvent.click(screen.getByRole('button', { name: '다른 화면으로' }));
    expect(screen.getByRole('complementary', { name: '보던 방송' })).toHaveTextContent('방송 7');
  });
});
