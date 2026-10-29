import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/api';
import { OverlayLayoutBar } from './OverlayLayoutBar';
import { OverlayLayoutEditor } from './OverlayLayoutEditor';
import { useOverlayLayoutEditing } from './use-overlay-layout-editing';

type Request = { path: string; method: string; init?: { body?: unknown } };
const state = vi.hoisted(() => ({ puts: [] as { path: string; body: unknown }[] }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: { PUT: (path: string, init: Request['init']) => ({ path, method: 'PUT', init }) },
    // 저장하면 받은 배치를 담은 방송 정보를 돌려준다.
    call: async (r: Request) => {
      state.puts.push({ path: r.path, body: r.init?.body });
      return { id: 1, status: 'LIVE', overlayLayout: r.init?.body };
    },
  };
});

/** 스튜디오처럼 편집 줄과 미리보기(편집 중이면 편집기)를 함께 그린다. */
function Harness({ onSaved }: { onSaved: (liveStream: Schema<'LiveStreamResponse'>) => void }) {
  const editing = useOverlayLayoutEditing({ id: 1, status: 'LIVE' }, onSaved);
  return (
    <>
      <OverlayLayoutBar editing={editing} />
      {editing.draft && <OverlayLayoutEditor layout={editing.draft} onChange={editing.setDraft} featured={[]} />}
    </>
  );
}

function renderHarness() {
  const onSaved = vi.fn();
  render(
    <QueryClientProvider client={new QueryClient()}>
      <Harness onSaved={onSaved} />
    </QueryClientProvider>,
  );
  return { onSaved };
}

describe('화면 배치 편집', () => {
  beforeEach(() => {
    state.puts = [];
  });

  it('[화면 배치 편집]을 누르면 네 가지 견본이 나오고, 화살표 키로 옮긴 뒤 저장하면 배치를 보낸다', async () => {
    const { onSaved } = renderHarness();
    expect(screen.queryByRole('group', { name: '화면 배치 편집' })).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: '화면 배치 편집' }));
    for (const label of ['경매 카드', '선물 안내', '입찰 안내', '낙찰 안내']) {
      expect(screen.getByRole('button', { name: `${label} 위치 옮기기` })).toBeInTheDocument();
    }
    // 바꾼 것이 없으면 저장할 수 없다.
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled();

    // 입찰 안내(기본: 위쪽 가운데 50·0)를 아래로 10%(Shift)씩 두 번, 오른쪽으로 2%
    const bid = screen.getByRole('button', { name: '입찰 안내 위치 옮기기' });
    fireEvent.keyDown(bid, { key: 'ArrowDown', shiftKey: true });
    fireEvent.keyDown(bid, { key: 'ArrowDown', shiftKey: true });
    fireEvent.keyDown(bid, { key: 'ArrowRight' });
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(state.puts).toHaveLength(1);
    expect(state.puts[0]?.path).toBe('/api/v1/live-streams/{liveStreamId}/overlay-layout');
    expect(state.puts[0]?.body).toMatchObject({ bid: { x: 52, y: 20 }, featured: { x: 0, y: 100 }, donation: { x: 100, y: 0 }, sold: { x: 50, y: 50 } });
    expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ overlayLayout: expect.objectContaining({ bid: { x: 52, y: 20, hidden: false } }) }));
    // 저장하면 편집을 마친다.
    expect(screen.queryByRole('group', { name: '화면 배치 편집' })).toBeNull();
  });

  it('[취소]하면 보내지 않고, [기본 위치로]는 기본 배치일 때 누를 수 없다', async () => {
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: '화면 배치 편집' }));
    expect(screen.getByRole('button', { name: '기본 위치로' })).toBeDisabled();

    fireEvent.keyDown(screen.getByRole('button', { name: '선물 안내 위치 옮기기' }), { key: 'ArrowLeft' });
    expect(screen.getByRole('button', { name: '기본 위치로' })).toBeEnabled();
    await userEvent.click(screen.getByRole('button', { name: '기본 위치로' }));
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: '취소' }));
    expect(screen.queryByRole('group', { name: '화면 배치 편집' })).toBeNull();
    expect(state.puts).toHaveLength(0);
  });

  it('기본 안내를 숨기면 견본이 사라지고, 숨김으로 저장한다', async () => {
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: '화면 배치 편집' }));

    await userEvent.click(screen.getByRole('button', { name: /선물 안내/, pressed: true }));
    expect(screen.queryByRole('button', { name: '선물 안내 위치 옮기기' })).toBeNull();
    expect(screen.getByRole('button', { name: /선물 안내/, pressed: false })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(state.puts[0]?.body).toMatchObject({ donation: { x: 100, y: 0, hidden: true }, bid: { hidden: false }, boxes: [] });
  });

  it('텍스트 박스를 넣고 글자·모양을 바꿔 저장하고, 빈 글자면 저장할 수 없다', async () => {
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: '화면 배치 편집' }));

    await userEvent.click(screen.getByRole('button', { name: '텍스트 추가' }));
    expect(screen.getByRole('button', { name: '텍스트 박스 1 위치 옮기기' })).toBeInTheDocument();
    const text = screen.getByRole('textbox', { name: '글자' });
    await userEvent.clear(text);
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled();
    await userEvent.type(text, '오늘 마감 특가');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: '모양' }), 'HIGHLIGHT');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(state.puts[0]?.body).toMatchObject({
      boxes: [{ type: 'TEXT', x: 50, y: 50, text: '오늘 마감 특가', textStyle: 'HIGHLIGHT', textSize: 'MEDIUM' }],
    });
  });

  it('고른 박스를 빼면 미리보기에서도 사라진다', async () => {
    renderHarness();
    await userEvent.click(screen.getByRole('button', { name: '화면 배치 편집' }));
    await userEvent.click(screen.getByRole('button', { name: '텍스트 추가' }));

    await userEvent.click(screen.getByRole('button', { name: '박스 빼기' }));
    expect(screen.queryByRole('button', { name: '텍스트 박스 1 위치 옮기기' })).toBeNull();
    expect(screen.getByRole('button', { name: '저장' })).toBeDisabled();
  });
});
