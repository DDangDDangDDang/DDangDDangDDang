import { LayoutTemplate } from 'lucide-react';
import { errorMessage } from '@/api';
import { Button, Notice } from '@/components/ui';
import { DEFAULT_OVERLAY_LAYOUT, sameLayout } from '@/lib/overlay-layout';
import type { OverlayLayoutEditing } from './use-overlay-layout-editing';

/** 미리보기 위의 [화면 배치 편집] 줄. 편집 중이면 안내와 [기본 위치로]·[취소]·[저장]을 보여 준다. */
export function OverlayLayoutBar({ editing }: { editing: OverlayLayoutEditing }) {
  const { draft, saved, save } = editing;
  if (!draft) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-lg border border-neutral-100 bg-neutral-0 px-4 py-2.5">
        <span className="text-label text-neutral-500">경매 카드와 선물·입찰·낙찰 안내가 시청자 영상 위 어디에 보일지 정할 수 있어요.</span>
        <Button variant="outline" size="sm" onClick={editing.start}>
          <LayoutTemplate className="size-4" aria-hidden="true" />
          화면 배치 편집
        </Button>
      </div>
    );
  }
  const changed = !sameLayout(draft, saved);
  return (
    <div role="region" aria-label="화면 배치 편집 중" className="flex flex-col gap-2 rounded-lg border border-brand-200 bg-brand-50 px-4 py-2.5">
      <div className="flex items-center justify-between gap-3">
        <span className="text-label text-neutral-900">
          <strong className="font-semibold text-brand-700">화면 배치 편집 중</strong> · 견본을 끌어서 옮기세요. 누른 뒤 화살표 키로도 옮길 수 있어요.
        </span>
        <div className="flex shrink-0 gap-2">
          <Button
            variant="ghost"
            size="sm"
            disabled={sameLayout(draft, DEFAULT_OVERLAY_LAYOUT) || save.isPending}
            onClick={() => editing.setDraft(DEFAULT_OVERLAY_LAYOUT)}
          >
            기본 위치로
          </Button>
          <Button variant="outline" size="sm" disabled={save.isPending} onClick={editing.cancel}>
            취소
          </Button>
          <Button size="sm" loading={save.isPending} disabled={!changed} onClick={() => save.mutate(draft)}>
            저장
          </Button>
        </div>
      </div>
      {save.isError && <Notice tone="error">{errorMessage(save.error)}</Notice>}
    </div>
  );
}
