import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/api';
import { ThumbnailField } from './ThumbnailField';

// 서버·저장소 요청은 하지 않고, 어떤 순서로 무엇을 보냈는지만 기록한다.
const state = vi.hoisted(() => ({ requests: [] as string[], uploadFails: false }));
vi.mock('@/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api')>()),
  api: {
    POST: (path: string, init: { body: { contentType: string } }) => ({ request: `POST ${path} ${init.body.contentType}` }),
    PUT: (path: string, init: { body: { thumbnailKey: string } }) => ({ request: `PUT ${path} ${init.body.thumbnailKey}` }),
    DELETE: (path: string) => ({ request: `DELETE ${path}` }),
  },
  call: async ({ request }: { request: string }) => {
    state.requests.push(request);
    if (request.startsWith('POST')) return { uploadUrl: 'https://upload', thumbnailKey: 'live-thumbnails/7/custom/a.png' };
    if (request.startsWith('PUT')) return { ...liveStream, customThumbnailUrl: 'https://cdn.test/live-thumbnails/7/custom/a.png' };
    return { ...liveStream, customThumbnailUrl: undefined };
  },
  uploadToSignedUrl: async (url: string) => {
    state.requests.push(`UPLOAD ${url}`);
    if (state.uploadFails) {
      const { ApiError } = await import('@/api/errors');
      throw new ApiError('UPLOAD_FAILED', '파일을 올리지 못했어요. 다시 시도해 주세요.', 500);
    }
  },
}));

const liveStream: Schema<'LiveStreamResponse'> & { id: number } = { id: 7, title: '필름 카메라 경매', status: 'SCHEDULED' };

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const png = (size = 1000) => new File([new Uint8Array(size)], '썸네일.png', { type: 'image/png' });

describe('방송 썸네일 고르기', () => {
  beforeEach(() => {
    state.requests = [];
    state.uploadFails = false;
  });

  it('만든 방송에서 이미지를 고르면 업로드 URL → 저장소 → 확정 순서로 올리고 바뀐 방송 정보를 넘긴다', async () => {
    const onChange = vi.fn();
    render(<ThumbnailField mode="edit" liveStream={liveStream} onChange={onChange} />, { wrapper });
    expect(screen.getByText('자동 캡처 사용 중')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '지우기' })).not.toBeInTheDocument();

    await userEvent.upload(screen.getByLabelText('썸네일 이미지 파일'), png());

    expect(await screen.findByText(/썸네일을 바꿨어요/)).toBeInTheDocument();
    expect(state.requests).toEqual([
      'POST /api/v1/live-streams/{liveStreamId}/custom-thumbnail-uploads image/png',
      'UPLOAD https://upload',
      'PUT /api/v1/live-streams/{liveStreamId}/custom-thumbnail live-thumbnails/7/custom/a.png',
    ]);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ customThumbnailUrl: 'https://cdn.test/live-thumbnails/7/custom/a.png' }));
  });

  it('올린 썸네일이 있으면 미리 보여 주고, 지우면 자동 캡처로 돌아간다고 알려 준다', async () => {
    const onChange = vi.fn();
    const { container } = render(
      <ThumbnailField mode="edit" liveStream={{ ...liveStream, customThumbnailUrl: 'https://cdn.test/a.png' }} onChange={onChange} />,
      { wrapper },
    );
    expect(container.querySelector('img')).toHaveAttribute('src', 'https://cdn.test/a.png');

    await userEvent.click(screen.getByRole('button', { name: '지우기' }));

    expect(await screen.findByText(/자동 캡처로 돌아가요/)).toBeInTheDocument();
    expect(state.requests).toEqual(['DELETE /api/v1/live-streams/{liveStreamId}/custom-thumbnail']);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ customThumbnailUrl: undefined }));
  });

  it('형식·크기가 맞지 않으면 올리지 않고 이유를 보여 준다', async () => {
    render(<ThumbnailField mode="edit" liveStream={liveStream} onChange={vi.fn()} />, { wrapper });

    await userEvent.upload(screen.getByLabelText('썸네일 이미지 파일'), png(2 * 1024 * 1024 + 1));

    expect(screen.getByText('2MB 이하 이미지만 올릴 수 있어요.')).toBeInTheDocument();
    expect(state.requests).toEqual([]);
  });

  it('저장소에 올리지 못하면 확정하지 않고 오류를 보여 준다', async () => {
    state.uploadFails = true;
    const onChange = vi.fn();
    render(<ThumbnailField mode="edit" liveStream={liveStream} onChange={onChange} />, { wrapper });

    await userEvent.upload(screen.getByLabelText('썸네일 이미지 파일'), png());

    expect(await screen.findByText('파일을 올리지 못했어요. 다시 시도해 주세요.')).toBeInTheDocument();
    expect(state.requests.some((r) => r.startsWith('PUT'))).toBe(false);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('방송을 만들 때는 고른 파일만 넘기고 바로 올리지 않는다', async () => {
    const onFileChange = vi.fn();
    render(<ThumbnailField mode="create" onFileChange={onFileChange} />, { wrapper });
    const file = png();

    await userEvent.upload(screen.getByLabelText('썸네일 이미지 파일'), file);

    expect(onFileChange).toHaveBeenCalledWith(file);
    expect(state.requests).toEqual([]);
  });
});
