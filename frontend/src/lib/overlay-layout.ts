import type { CSSProperties } from 'react';
import type { Schema } from '@/api';

/*
 * 방송 화면 배치. 판매자가 스튜디오에서 영상 위 UI(경매 카드·선물·입찰·낙찰 안내)를 끌어 옮기거나 숨기고,
 * 직접 텍스트·이미지 박스를 넣는다. 시청 화면도 같은 자리에 그린다.
 * 위치는 x·y 0~100(%)이다. 0이면 왼쪽(위쪽) 끝, 100이면 오른쪽(아래쪽) 끝, 50이면 가운데에 붙는다.
 * 남는 공간(배치 영역 크기 - UI 크기)에 대한 비율이라, 화면 크기가 달라도 UI가 영역 밖으로 나가지 않는다.
 */

export type OverlayKey = 'featured' | 'donation' | 'bid' | 'sold';
/** hidden이 true면 판매자가 숨긴 UI라 시청 화면에 그리지 않는다. */
export type OverlayPosition = { x: number; y: number; hidden?: boolean };

export type OverlayTextStyle = 'PLAIN' | 'HIGHLIGHT' | 'DARK';
export type OverlayTextSize = 'SMALL' | 'MEDIUM' | 'LARGE';

/**
 * 판매자가 넣은 박스. id는 화면에서만 쓰는 구분값이다. (서버에 보내지 않음)
 * - 텍스트 박스: text·textStyle·textSize
 * - 이미지 박스: imageKey(저장소 키)·imageUrl(보여 줄 주소)·width(영상 폭의 %)
 */
export type OverlayBox =
  | { id: string; type: 'TEXT'; x: number; y: number; text: string; textStyle: OverlayTextStyle; textSize: OverlayTextSize }
  | { id: string; type: 'IMAGE'; x: number; y: number; imageKey: string; imageUrl: string; width: number };

export type OverlayLayout = Record<OverlayKey, OverlayPosition> & { boxes: OverlayBox[] };

export const OVERLAY_KEYS: OverlayKey[] = ['featured', 'donation', 'bid', 'sold'];

export const OVERLAY_LABELS: Record<OverlayKey, string> = {
  featured: '경매 카드',
  donation: '선물 안내',
  bid: '입찰 안내',
  sold: '낙찰 안내',
};

/** 서버(OverlayLayoutRequest·OverlayBoxRequest)와 같은 한도 */
export const MAX_OVERLAY_BOXES = 10;
export const MAX_OVERLAY_TEXT_LENGTH = 40;
export const MIN_OVERLAY_IMAGE_WIDTH = 10;
export const MAX_OVERLAY_IMAGE_WIDTH = 60;
export const DEFAULT_OVERLAY_IMAGE_WIDTH = 25;

export const OVERLAY_TEXT_STYLE_LABELS: Record<OverlayTextStyle, string> = {
  PLAIN: '바탕 없음',
  HIGHLIGHT: '노란 바탕',
  DARK: '어두운 바탕',
};

export const OVERLAY_TEXT_SIZE_LABELS: Record<OverlayTextSize, string> = {
  SMALL: '작게',
  MEDIUM: '보통',
  LARGE: '크게',
};

/** 판매자가 정하지 않았을 때의 배치: 경매 카드 왼쪽 아래, 선물 오른쪽 위, 입찰 위쪽 가운데, 낙찰 가운데, 박스 없음 */
export const DEFAULT_OVERLAY_LAYOUT: OverlayLayout = {
  featured: { x: 0, y: 100 },
  donation: { x: 100, y: 0 },
  bid: { x: 50, y: 0 },
  sold: { x: 50, y: 50 },
  boxes: [],
};

/**
 * 배치 영역: 영상 전체. 판매자가 보는 방송 화면 끝까지 옮길 수 있다.
 * 영상 위 UI는 가장자리에서 OverlayItem의 여백(16px)만큼 떨어져 그려지고, LIVE 배지·버튼 줄은 이 영역보다 위(z-10)에 그려 가려지지 않는다.
 * isolate로 영역 안의 z-index가 배지·버튼과 섞이지 않게 한다.
 */
export const OVERLAY_AREA_CLASS = 'absolute inset-0 isolate';

/** 서버 배치(없으면 null)를 화면에서 쓸 배치로. 빠진 값은 기본 배치로 채우고, 모양이 맞지 않는 박스는 뺀다. */
export function resolveOverlayLayout(saved: Schema<'OverlayLayoutResponse'> | null | undefined): OverlayLayout {
  const pick = (key: OverlayKey): OverlayPosition => {
    const position = saved?.[key];
    if (!position) return DEFAULT_OVERLAY_LAYOUT[key];
    const resolved: OverlayPosition = { x: clampPercent(position.x), y: clampPercent(position.y) };
    if (position.hidden) resolved.hidden = true;
    return resolved;
  };
  const boxes = (saved?.boxes ?? []).flatMap((box, index): OverlayBox[] => {
    const base = { id: `saved-${index}`, x: clampPercent(box.x ?? 50), y: clampPercent(box.y ?? 50) };
    if (box.type === 'TEXT' && box.text) {
      return [{ ...base, type: 'TEXT', text: box.text, textStyle: box.textStyle ?? 'PLAIN', textSize: box.textSize ?? 'MEDIUM' }];
    }
    if (box.type === 'IMAGE' && box.imageKey && box.imageUrl) {
      return [{ ...base, type: 'IMAGE', imageKey: box.imageKey, imageUrl: box.imageUrl, width: box.width ?? DEFAULT_OVERLAY_IMAGE_WIDTH }];
    }
    return [];
  });
  return { featured: pick('featured'), donation: pick('donation'), bid: pick('bid'), sold: pick('sold'), boxes };
}

/** 저장 요청 본문. 박스의 화면용 id·imageUrl은 보내지 않는다. */
export function toOverlayLayoutRequest(layout: OverlayLayout): Schema<'OverlayLayoutRequest'> {
  const position = (key: OverlayKey) => ({ x: layout[key].x, y: layout[key].y, hidden: layout[key].hidden === true });
  return {
    featured: position('featured'),
    donation: position('donation'),
    bid: position('bid'),
    sold: position('sold'),
    boxes: layout.boxes.map((box) =>
      box.type === 'TEXT'
        ? { type: 'TEXT', x: box.x, y: box.y, text: box.text, textStyle: box.textStyle, textSize: box.textSize }
        : { type: 'IMAGE', x: box.x, y: box.y, imageKey: box.imageKey, width: box.width },
    ),
  };
}

/** 배치 영역 안의 위치 → CSS. left·top을 x·y%로 두고, UI 자기 크기의 x·y%만큼 되돌려 남는 공간 안에 놓는다. */
export function overlayPositionStyle({ x, y }: { x: number; y: number }): CSSProperties {
  return { left: `${x}%`, top: `${y}%`, transform: `translate(-${x}%, -${y}%)` };
}

export function clampPercent(value: number): number {
  return Math.min(100, Math.max(0, Math.round(value)));
}

/** 끌어 놓을 때 가장자리·가운데에 이만큼(%) 가까우면 붙인다. */
export const SNAP_PERCENT = 3;

export function snapPercent(value: number): number {
  const clamped = clampPercent(value);
  const target = [0, 50, 100].find((point) => Math.abs(point - clamped) <= SNAP_PERCENT);
  return target ?? clamped;
}

/**
 * 끈 거리(px)를 새 위치(%)로. 남는 공간(영역 - UI 크기)에 대한 비율로 바꾸고 가장자리·가운데에 붙인다.
 * 남는 공간이 없으면(UI가 영역만큼 크면) 그대로 둔다.
 */
export function dragPercent(start: number, deltaPx: number, freePx: number): number {
  if (freePx <= 0) return start;
  return snapPercent(start + (deltaPx / freePx) * 100);
}

/** 키보드로 옮기기: 화살표 한 번에 2%, Shift를 누르면 10% */
export function keyboardMove(position: { x: number; y: number }, key: string, shift: boolean): { x: number; y: number } | undefined {
  const step = shift ? 10 : 2;
  const delta: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
  const move = delta[key];
  if (!move) return undefined;
  return { x: clampPercent(position.x + move[0]), y: clampPercent(position.y + move[1]) };
}

/** 저장할 내용이 같은지 (박스의 화면용 id는 보지 않는다) */
export function sameLayout(a: OverlayLayout, b: OverlayLayout): boolean {
  return JSON.stringify(toOverlayLayoutRequest(a)) === JSON.stringify(toOverlayLayoutRequest(b));
}

/** 새 텍스트 박스: 가운데에 보통 크기, 바탕 없음 */
export function newTextBox(): OverlayBox {
  return { id: crypto.randomUUID(), type: 'TEXT', x: 50, y: 50, text: '텍스트를 입력하세요', textStyle: 'PLAIN', textSize: 'MEDIUM' };
}

/** 새 이미지 박스: 오른쪽 위에 영상 폭의 25% */
export function newImageBox(imageKey: string, imageUrl: string): OverlayBox {
  return { id: crypto.randomUUID(), type: 'IMAGE', x: 100, y: 0, imageKey, imageUrl, width: DEFAULT_OVERLAY_IMAGE_WIDTH };
}
