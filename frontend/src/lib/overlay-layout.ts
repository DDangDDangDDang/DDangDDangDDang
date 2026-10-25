import type { CSSProperties } from 'react';
import type { Schema } from '@/api';

/*
 * 방송 화면 배치. 판매자가 스튜디오에서 영상 위 UI(경매 카드·선물·입찰·낙찰 안내)를 끌어 옮기고, 시청 화면도 같은 자리에 그린다.
 * 위치는 x·y 0~100(%)이다. 0이면 왼쪽(위쪽) 끝, 100이면 오른쪽(아래쪽) 끝, 50이면 가운데에 붙는다.
 * 남는 공간(배치 영역 크기 - UI 크기)에 대한 비율이라, 화면 크기가 달라도 UI가 영역 밖으로 나가지 않는다.
 */

export type OverlayKey = 'featured' | 'donation' | 'bid' | 'sold';
export type OverlayPosition = { x: number; y: number };
export type OverlayLayout = Record<OverlayKey, OverlayPosition>;

export const OVERLAY_KEYS: OverlayKey[] = ['featured', 'donation', 'bid', 'sold'];

export const OVERLAY_LABELS: Record<OverlayKey, string> = {
  featured: '경매 카드',
  donation: '선물 안내',
  bid: '입찰 안내',
  sold: '낙찰 안내',
};

/** 판매자가 정하지 않았을 때의 배치: 경매 카드 왼쪽 아래, 선물 오른쪽 위, 입찰 위쪽 가운데, 낙찰 가운데 */
export const DEFAULT_OVERLAY_LAYOUT: OverlayLayout = {
  featured: { x: 0, y: 100 },
  donation: { x: 100, y: 0 },
  bid: { x: 50, y: 0 },
  sold: { x: 50, y: 50 },
};

/**
 * 배치 영역: 영상에서 왼쪽 위 LIVE·시청자 수 배지와 아래쪽 버튼 줄(시청 화면의 소리·전체 화면, 스튜디오의 마이크·카메라)을 뺀 곳.
 * 이 안에서만 움직이므로 어디에 두어도 버튼을 가리지 않는다.
 */
export const OVERLAY_AREA_CLASS = 'absolute inset-x-4 top-14 bottom-16';

/** 서버 배치(없으면 null)를 화면에서 쓸 배치로. 빠진 값은 기본 배치로 채운다. */
export function resolveOverlayLayout(saved: Schema<'OverlayLayoutResponse'> | null | undefined): OverlayLayout {
  const pick = (key: OverlayKey): OverlayPosition => {
    const position = saved?.[key];
    if (!position) return DEFAULT_OVERLAY_LAYOUT[key];
    return { x: clampPercent(position.x), y: clampPercent(position.y) };
  };
  return { featured: pick('featured'), donation: pick('donation'), bid: pick('bid'), sold: pick('sold') };
}

/** 배치 영역 안의 위치 → CSS. left·top을 x·y%로 두고, UI 자기 크기의 x·y%만큼 되돌려 남는 공간 안에 놓는다. */
export function overlayPositionStyle({ x, y }: OverlayPosition): CSSProperties {
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
export function keyboardMove(position: OverlayPosition, key: string, shift: boolean): OverlayPosition | undefined {
  const step = shift ? 10 : 2;
  const delta: Record<string, [number, number]> = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
  const move = delta[key];
  if (!move) return undefined;
  return { x: clampPercent(position.x + move[0]), y: clampPercent(position.y + move[1]) };
}

export function sameLayout(a: OverlayLayout, b: OverlayLayout): boolean {
  return OVERLAY_KEYS.every((key) => a[key].x === b[key].x && a[key].y === b[key].y);
}
