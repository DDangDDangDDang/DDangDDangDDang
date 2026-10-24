import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, type Schema } from '@/api';
import { resolveOverlayLayout, type OverlayLayout } from '@/lib/overlay-layout';

type LiveStream = Schema<'LiveStreamResponse'>;

/**
 * 스튜디오 화면 배치 편집 상태. draft가 있으면 편집 중이고, 미리보기 위에 견본을 띄워 옮긴다.
 * 저장하면 PUT /live-streams/{id}/overlay-layout → 응답(방송 정보)으로 스튜디오를 바꾸고, 시청 화면에는 방송 정보 변경으로 바로 반영된다.
 */
export function useOverlayLayoutEditing(liveStream: LiveStream & { id: number }, onSaved: (liveStream: LiveStream) => void) {
  const saved = resolveOverlayLayout(liveStream.overlayLayout);
  const [draft, setDraft] = useState<OverlayLayout>();
  const save = useMutation({
    mutationFn: (layout: OverlayLayout) =>
      call(api.PUT('/api/v1/live-streams/{liveStreamId}/overlay-layout', { params: { path: { liveStreamId: liveStream.id } }, body: layout })),
    onSuccess: (updated) => {
      if (updated) onSaved(updated);
      setDraft(undefined);
    },
  });
  return {
    saved,
    draft,
    start: () => {
      save.reset();
      setDraft(saved);
    },
    cancel: () => setDraft(undefined),
    setDraft,
    save,
  };
}

export type OverlayLayoutEditing = ReturnType<typeof useOverlayLayoutEditing>;
