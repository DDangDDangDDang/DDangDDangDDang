import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { THUMBNAIL_INTERVAL_MS } from '@/lib/live-thumbnail';
import { FIRST_CAPTURE_DELAY_MS, useThumbnailCapture } from './use-thumbnail-capture';

// 화면 캡처와 서버·저장소 요청은 하지 않고, 어떤 순서로 무엇을 보냈는지만 기록한다.
const state = vi.hoisted(() => ({ requests: [] as string[], captureFails: false, uploadDelayMs: 0 }));
vi.mock('@/lib/live-thumbnail', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/live-thumbnail')>()),
  captureJpeg: async () => (state.captureFails ? undefined : new Blob(['jpg'], { type: 'image/jpeg' })),
}));
vi.mock('@/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api')>()),
  api: {
    POST: (path: string, init: { body: { contentType: string } }) => ({ request: `POST ${path} ${init.body.contentType}` }),
    PUT: (path: string, init: { body: { thumbnailKey: string } }) => ({ request: `PUT ${path} ${init.body.thumbnailKey}` }),
  },
  call: async ({ request }: { request: string }) => {
    state.requests.push(request);
    return request.startsWith('POST') ? { uploadUrl: 'https://upload', thumbnailKey: 'live-thumbnails/7/a.jpg' } : undefined;
  },
  uploadToSignedUrl: async (url: string) => {
    state.requests.push(`UPLOAD ${url}`);
    if (state.uploadDelayMs > 0) await new Promise((resolve) => setTimeout(resolve, state.uploadDelayMs));
  },
}));

const track = {} as MediaStreamTrack;

/** 예약된 캡처를 돌리고 이어지는 요청(Promise)까지 끝낸다. */
async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

describe('송출 화면 썸네일 올리기', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    state.requests = [];
    state.captureFails = false;
    state.uploadDelayMs = 0;
  });
  afterEach(() => vi.useRealTimers());

  it('송출이 연결되면 잠시 뒤 첫 썸네일을 올리고, 이후 1분마다 다시 올린다', async () => {
    renderHook(() => useThumbnailCapture(7, track));
    await advance(FIRST_CAPTURE_DELAY_MS);
    expect(state.requests).toEqual([
      'POST /api/v1/live-streams/{liveStreamId}/thumbnail-uploads image/jpeg',
      'UPLOAD https://upload',
      'PUT /api/v1/live-streams/{liveStreamId}/thumbnail live-thumbnails/7/a.jpg',
    ]);

    await advance(THUMBNAIL_INTERVAL_MS);
    expect(state.requests.filter((request) => request.startsWith('PUT'))).toHaveLength(2);
  });

  it('송출 중이 아니거나 화면이 없으면 올리지 않고, 송출을 멈추면 그만 올린다', async () => {
    const { rerender } = renderHook(({ id }) => useThumbnailCapture(id, track), { initialProps: { id: undefined as number | undefined } });
    await advance(THUMBNAIL_INTERVAL_MS);
    expect(state.requests).toEqual([]);

    rerender({ id: 7 });
    await advance(FIRST_CAPTURE_DELAY_MS);
    expect(state.requests).toHaveLength(3);
    rerender({ id: undefined });
    await advance(THUMBNAIL_INTERVAL_MS * 2);
    expect(state.requests).toHaveLength(3);
  });

  it('앞 차례 업로드가 끝나지 않았으면 다음 차례는 건너뛴다', async () => {
    state.uploadDelayMs = THUMBNAIL_INTERVAL_MS * 1.5;
    renderHook(() => useThumbnailCapture(7, track));
    await advance(FIRST_CAPTURE_DELAY_MS + THUMBNAIL_INTERVAL_MS);
    // 첫 업로드가 아직 끝나지 않아 1분 차례의 캡처는 보내지 않았다.
    expect(state.requests.filter((request) => request.startsWith('POST'))).toHaveLength(1);

    await advance(THUMBNAIL_INTERVAL_MS);
    expect(state.requests.filter((request) => request.startsWith('PUT'))).toHaveLength(1);
    await advance(THUMBNAIL_INTERVAL_MS);
    expect(state.requests.filter((request) => request.startsWith('POST'))).toHaveLength(2);
  });

  it('화면을 캡처하지 못하면 아무것도 보내지 않는다', async () => {
    state.captureFails = true;
    renderHook(() => useThumbnailCapture(7, track));
    await advance(FIRST_CAPTURE_DELAY_MS);
    expect(state.requests).toEqual([]);
  });
});
