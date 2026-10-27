import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Broadcast } from '@/components/broadcast/broadcast-context';
import { StudioStage } from './StudioStage';

const idle = { enabled: true, starting: false, devices: [] };

type Recorded = Broadcast['source']['recorded'];

function renderStage(source: { kind?: Broadcast['source']['kind']; recorded?: Partial<Recorded> } = {}) {
  const setKind = vi.fn();
  const select = vi.fn();
  render(
    <StudioStage
      live={false}
      camera={idle}
      microphone={idle}
      source={{ kind: source.kind ?? 'camera', setKind, recorded: { fileName: undefined, error: undefined, loading: false, select, ...source.recorded } }}
      publisher={{ phase: 'idle', retry: () => undefined }}
      onToggle={() => undefined}
      onSelectDevice={() => undefined}
    />,
  );
  return { setKind, select };
}

describe('StudioStage', () => {
  it('송출할 영상을 카메라·녹화 영상 중에서 고른다', async () => {
    const { setKind } = renderStage();
    expect(screen.getByRole('radio', { name: '카메라' })).toBeChecked();
    expect(screen.getByLabelText('카메라')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: '녹화 영상' }));
    expect(setKind).toHaveBeenCalledWith('recorded');
  });

  it('녹화 영상이면 장치 선택 대신 영상 파일을 고르고, 반복 재생·녹화 영상 표시를 알려 준다', async () => {
    const { select } = renderStage({ kind: 'recorded' });
    expect(screen.queryByRole('combobox', { name: '카메라' })).not.toBeInTheDocument();
    expect(screen.getByText('송출할 영상 파일을 골라 주세요')).toBeInTheDocument();
    expect(screen.getByText(/영상이 끝나면 처음부터 다시 재생돼요/)).toHaveTextContent('녹화 영상');

    const file = new File(['video'], '상품 소개.mp4', { type: 'video/mp4' });
    await userEvent.upload(screen.getByLabelText('영상 파일 고르기'), file);
    expect(select).toHaveBeenCalledWith(file);
  });

  it('고른 파일 이름과 재생하지 못한 이유를 보여 준다', () => {
    renderStage({ kind: 'recorded', recorded: { fileName: '상품 소개.mp4', error: '이 영상을 재생하지 못했어요.' } });
    expect(screen.getByText('상품 소개.mp4')).toBeInTheDocument();
    expect(screen.getByLabelText('다른 영상 고르기')).toBeInTheDocument();
    expect(screen.getByText('이 영상을 재생하지 못했어요.')).toBeInTheDocument();
  });
});
