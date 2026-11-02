import { Eye, EyeOff, ImagePlus, LayoutTemplate, Trash2, Type } from 'lucide-react';
import { useRef } from 'react';
import { errorMessage } from '@/api';
import { Button, Input, Notice, Select } from '@/components/ui';
import { cn } from '@/lib/cn';
import {
  DEFAULT_OVERLAY_LAYOUT,
  MAX_OVERLAY_BOXES,
  MAX_OVERLAY_IMAGE_WIDTH,
  MAX_OVERLAY_TEXT_LENGTH,
  MIN_OVERLAY_IMAGE_WIDTH,
  OVERLAY_KEYS,
  OVERLAY_LABELS,
  OVERLAY_TEXT_SIZE_LABELS,
  OVERLAY_TEXT_STYLE_LABELS,
  sameLayout,
  type OverlayBox,
  type OverlayLayout,
  type OverlayTextSize,
  type OverlayTextStyle,
} from '@/lib/overlay-layout';
import { PROFILE_IMAGE_TYPES } from '@/lib/profile-image';
import type { OverlayLayoutEditing } from './use-overlay-layout-editing';

/**
 * 미리보기 위의 [화면 배치 편집] 줄. 편집 중이면 안내와 [기본 위치로]·[취소]·[저장], 그리고 편집 패널을 보여 준다.
 * 편집 패널: 기본 안내 보이기·숨기기, 텍스트·이미지 박스 넣기, 고른 박스의 글자·모양·크기 바꾸기·빼기.
 */
export function OverlayLayoutBar({ editing }: { editing: OverlayLayoutEditing }) {
  const { draft, saved, save } = editing;
  if (!draft) {
    return (
      <div className="flex items-center justify-between gap-3 rounded-lg border border-neutral-100 bg-neutral-0 px-4 py-2.5">
        <span className="text-label text-neutral-500">경매 카드와 선물·입찰·낙찰 안내의 위치를 정하거나 숨기고, 영상 위에 글자·이미지를 넣을 수 있어요.</span>
        <Button variant="outline" size="sm" onClick={editing.start}>
          <LayoutTemplate className="size-4" aria-hidden="true" />
          화면 배치 편집
        </Button>
      </div>
    );
  }
  const changed = !sameLayout(draft, saved);
  // 빈 텍스트 박스는 서버도 막는다(LIVESTREAM_019). 고칠 곳은 박스 고치기 칸에 보여 준다.
  const blankText = draft.boxes.some((box) => box.type === 'TEXT' && box.text.trim() === '');
  // 기본 위치로: 안내 위치·숨김만 처음대로 되돌리고, 넣은 박스는 그대로 둔다.
  const defaults: OverlayLayout = { ...DEFAULT_OVERLAY_LAYOUT, boxes: draft.boxes };
  return (
    <div role="region" aria-label="화면 배치 편집 중" className="flex flex-col gap-3 rounded-lg border border-brand-200 bg-brand-50 px-4 py-2.5">
      <div className="flex items-center justify-between gap-3">
        <span className="text-label text-neutral-900">
          <strong className="font-semibold text-brand-700">화면 배치 편집 중</strong> · 견본과 박스를 끌어서 옮기세요. 누른 뒤 화살표 키로도 옮길 수 있어요.
        </span>
        <div className="flex shrink-0 gap-2">
          <Button variant="ghost" size="sm" disabled={sameLayout(draft, defaults) || save.isPending} onClick={() => editing.setDraft(defaults)}>
            기본 위치로
          </Button>
          <Button variant="outline" size="sm" disabled={save.isPending} onClick={editing.cancel}>
            취소
          </Button>
          <Button size="sm" loading={save.isPending} disabled={!changed || blankText || editing.uploadImage.isPending} onClick={() => save.mutate(draft)}>
            저장
          </Button>
        </div>
      </div>
      <OverlayLayoutPanel editing={editing} layout={draft} />
      {save.isError && <Notice tone="error">{errorMessage(save.error)}</Notice>}
    </div>
  );
}

function OverlayLayoutPanel({ editing, layout }: { editing: OverlayLayoutEditing; layout: OverlayLayout }) {
  const fileRef = useRef<HTMLInputElement>(null);
  const full = layout.boxes.length >= MAX_OVERLAY_BOXES;
  const selectedBox = layout.boxes.find((box) => box.id === editing.selected);

  return (
    <div className="flex flex-col gap-3 border-t border-brand-200 pt-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="w-16 shrink-0 text-label font-semibold text-neutral-700">기본 안내</span>
        {OVERLAY_KEYS.map((key) => {
          const hidden = layout[key].hidden === true;
          return (
            <button
              key={key}
              type="button"
              aria-pressed={!hidden}
              onClick={() => editing.toggleHidden(key)}
              className={cn(
                'flex h-9 items-center gap-1.5 rounded-full border px-3 text-label font-semibold transition-colors',
                hidden ? 'border-neutral-200 text-neutral-500 hover:bg-neutral-50' : 'border-brand-500 bg-brand-100 text-brand-700',
              )}
            >
              {hidden ? <EyeOff className="size-4" aria-hidden="true" /> : <Eye className="size-4" aria-hidden="true" />}
              {OVERLAY_LABELS[key]}
              <span className="sr-only">{hidden ? '(숨김)' : '(보임)'}</span>
            </button>
          );
        })}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <span className="w-16 shrink-0 text-label font-semibold text-neutral-700">내 박스</span>
        <Button variant="outline" size="sm" disabled={full} onClick={editing.addTextBox}>
          <Type className="size-4" aria-hidden="true" />
          텍스트 추가
        </Button>
        <Button variant="outline" size="sm" disabled={full} loading={editing.uploadImage.isPending} onClick={() => fileRef.current?.click()}>
          <ImagePlus className="size-4" aria-hidden="true" />
          이미지 추가
        </Button>
        <input
          ref={fileRef}
          type="file"
          accept={PROFILE_IMAGE_TYPES.join(',')}
          aria-label="방송 화면에 넣을 이미지 고르기"
          className="sr-only"
          tabIndex={-1}
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = '';
            if (file) editing.addImageBox(file);
          }}
        />
        <span className="num text-caption text-neutral-500">
          {layout.boxes.length} / {MAX_OVERLAY_BOXES}
        </span>
        <span className="text-caption text-neutral-500">· 박스를 누르면 아래에서 고칠 수 있어요.</span>
      </div>

      {editing.imageProblem && <Notice tone="error">{editing.imageProblem}</Notice>}
      {editing.uploadImage.isError && <Notice tone="error">{errorMessage(editing.uploadImage.error)}</Notice>}

      {selectedBox && <BoxInspector box={selectedBox} index={layout.boxes.indexOf(selectedBox)} editing={editing} />}
    </div>
  );
}

/** 고른 박스 고치기: 텍스트는 글자·모양·크기, 이미지는 크기. 둘 다 빼기. */
function BoxInspector({ box, index, editing }: { box: OverlayBox; index: number; editing: OverlayLayoutEditing }) {
  const title = `${box.type === 'TEXT' ? '텍스트 박스' : '이미지 박스'} ${index + 1}`;
  return (
    <div role="group" aria-label={`${title} 고치기`} className="flex flex-wrap items-end gap-3 rounded-md bg-neutral-0 px-3 py-3">
      <strong className="self-center text-label text-neutral-900">{title}</strong>
      {box.type === 'TEXT' ? (
        <>
          <Input
            label="글자"
            className="w-72"
            value={box.text}
            maxLength={MAX_OVERLAY_TEXT_LENGTH}
            hint={`${box.text.length} / ${MAX_OVERLAY_TEXT_LENGTH}자`}
            error={box.text.trim() === '' ? '글자를 입력해 주세요.' : undefined}
            onChange={(event) => editing.updateBox(box.id, { text: event.target.value })}
          />
          <Select
            label="모양"
            value={box.textStyle}
            options={Object.entries(OVERLAY_TEXT_STYLE_LABELS).map(([value, label]) => ({ value, label }))}
            onChange={(event) => editing.updateBox(box.id, { textStyle: event.target.value as OverlayTextStyle })}
          />
          <Select
            label="크기"
            value={box.textSize}
            options={Object.entries(OVERLAY_TEXT_SIZE_LABELS).map(([value, label]) => ({ value, label }))}
            onChange={(event) => editing.updateBox(box.id, { textSize: event.target.value as OverlayTextSize })}
          />
        </>
      ) : (
        <label className="flex flex-col gap-1.5 text-label font-semibold whitespace-nowrap text-neutral-900">
          <span>
            크기 · 영상 폭의 <span className="num">{box.width}%</span>
          </span>
          <input
            type="range"
            min={MIN_OVERLAY_IMAGE_WIDTH}
            max={MAX_OVERLAY_IMAGE_WIDTH}
            step={1}
            value={box.width}
            onChange={(event) => editing.updateBox(box.id, { width: Number(event.target.value) })}
            className="w-56 accent-brand-500"
          />
        </label>
      )}
      <Button variant="ghost" size="sm" className="ml-auto" onClick={() => editing.removeBox(box.id)}>
        <Trash2 className="size-4" aria-hidden="true" />
        박스 빼기
      </Button>
    </div>
  );
}
