import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, type Schema } from '@/api';
import { uploadToSignedUrl } from '@/api/upload';
import {
  MAX_OVERLAY_BOXES,
  newImageBox,
  newTextBox,
  resolveOverlayLayout,
  toOverlayLayoutRequest,
  type OverlayBox,
  type OverlayKey,
  type OverlayLayout,
} from '@/lib/overlay-layout';
import { profileImageProblem } from '@/lib/profile-image';

type LiveStream = Schema<'LiveStreamResponse'>;

/** 편집기에서 고른 것: 기본 안내(OverlayKey) 또는 판매자가 넣은 박스의 id */
export type OverlaySelection = OverlayKey | string;

/**
 * 스튜디오 화면 배치 편집 상태. draft가 있으면 편집 중이고, 미리보기 위에 견본·박스를 띄워 옮긴다.
 * - 기본 안내(경매 카드·선물·입찰·낙찰)는 숨기거나 다시 보이게 할 수 있다.
 * - 텍스트·이미지 박스를 넣고, 고른 박스의 글자·모양·크기를 바꾸거나 뺄 수 있다. (최대 MAX_OVERLAY_BOXES개)
 * - 이미지는 고르는 즉시 저장소에 올리고(업로드 URL), 저장할 때 박스로 확정된다.
 * 저장하면 PUT /live-streams/{id}/overlay-layout → 응답(방송 정보)으로 스튜디오를 바꾸고, 시청 화면에는 방송 정보 변경으로 바로 반영된다.
 */
export function useOverlayLayoutEditing(liveStream: LiveStream & { id: number }, onSaved: (liveStream: LiveStream) => void) {
  const saved = resolveOverlayLayout(liveStream.overlayLayout);
  const [draft, setDraft] = useState<OverlayLayout>();
  const [selected, setSelected] = useState<OverlaySelection>();
  const [imageProblem, setImageProblem] = useState<string>();

  const save = useMutation({
    mutationFn: (layout: OverlayLayout) =>
      call(
        api.PUT('/api/v1/live-streams/{liveStreamId}/overlay-layout', {
          params: { path: { liveStreamId: liveStream.id } },
          body: toOverlayLayoutRequest(layout),
        }),
      ),
    onSuccess: (updated) => {
      if (updated) onSaved(updated);
      setDraft(undefined);
      setSelected(undefined);
    },
  });

  const update = (change: (layout: OverlayLayout) => OverlayLayout) => setDraft((current) => (current ? change(current) : current));
  const addBox = (box: OverlayBox) => {
    update((layout) => (layout.boxes.length >= MAX_OVERLAY_BOXES ? layout : { ...layout, boxes: [...layout.boxes, box] }));
    setSelected(box.id);
  };

  const uploadImage = useMutation({
    mutationFn: async (file: File) => {
      const signed = await call(
        api.POST('/api/v1/live-streams/{liveStreamId}/overlay-images', {
          params: { path: { liveStreamId: liveStream.id } },
          body: { contentType: file.type, contentLength: file.size },
        }),
      );
      if (!signed?.uploadUrl || !signed.imageKey || !signed.imageUrl) throw new Error('업로드 URL을 받지 못했어요.');
      await uploadToSignedUrl(signed.uploadUrl, file);
      return { imageKey: signed.imageKey, imageUrl: signed.imageUrl };
    },
    onSuccess: ({ imageKey, imageUrl }) => addBox(newImageBox(imageKey, imageUrl)),
  });

  return {
    saved,
    draft,
    selected,
    select: setSelected,
    start: () => {
      save.reset();
      uploadImage.reset();
      setImageProblem(undefined);
      setDraft(saved);
      setSelected(undefined);
    },
    cancel: () => {
      setDraft(undefined);
      setSelected(undefined);
    },
    setDraft,
    /** 기본 안내를 숨기거나 다시 보이게 한다. 숨기면 고른 것도 풀린다. */
    toggleHidden: (key: OverlayKey) => {
      update((layout) => ({ ...layout, [key]: { ...layout[key], hidden: !layout[key].hidden } }));
      setSelected((current) => (current === key ? undefined : current));
    },
    addTextBox: () => addBox(newTextBox()),
    /** 이미지 파일을 골랐을 때: 서버와 같은 규칙(형식·5MB)으로 먼저 거르고 올린다. */
    addImageBox: (file: File) => {
      const problem = profileImageProblem(file);
      setImageProblem(problem);
      if (!problem) uploadImage.mutate(file);
    },
    imageProblem,
    uploadImage,
    updateBox: (id: string, change: Partial<OverlayBox>) =>
      update((layout) => ({ ...layout, boxes: layout.boxes.map((box) => (box.id === id ? ({ ...box, ...change } as OverlayBox) : box)) })),
    removeBox: (id: string) => {
      update((layout) => ({ ...layout, boxes: layout.boxes.filter((box) => box.id !== id) }));
      setSelected(undefined);
    },
    save,
  };
}

export type OverlayLayoutEditing = ReturnType<typeof useOverlayLayoutEditing>;
