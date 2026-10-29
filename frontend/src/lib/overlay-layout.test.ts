import { describe, expect, it } from 'vitest';
import {
  DEFAULT_OVERLAY_LAYOUT,
  dragPercent,
  keyboardMove,
  overlayPositionStyle,
  resolveOverlayLayout,
  sameLayout,
  snapPercent,
  toOverlayLayoutRequest,
} from './overlay-layout';

describe('resolveOverlayLayout', () => {
  it('정하지 않은 방송은 기본 배치', () => {
    expect(resolveOverlayLayout(null)).toEqual(DEFAULT_OVERLAY_LAYOUT);
    expect(resolveOverlayLayout(undefined)).toEqual(DEFAULT_OVERLAY_LAYOUT);
  });

  it('저장한 위치를 쓰고, 빠진 값은 기본 위치로 채운다', () => {
    const layout = resolveOverlayLayout({ featured: { x: 100, y: 0 }, bid: { x: 20, y: 30 }, sold: { x: 50, y: 50 } });
    expect(layout.featured).toEqual({ x: 100, y: 0 });
    expect(layout.donation).toEqual(DEFAULT_OVERLAY_LAYOUT.donation);
    expect(layout.bid).toEqual({ x: 20, y: 30 });
  });
});

describe('overlayPositionStyle', () => {
  it('왼쪽 위·가운데·오른쪽 아래 끝에 붙는 CSS', () => {
    expect(overlayPositionStyle({ x: 0, y: 0 })).toEqual({ left: '0%', top: '0%', transform: 'translate(-0%, -0%)' });
    expect(overlayPositionStyle({ x: 50, y: 50 })).toEqual({ left: '50%', top: '50%', transform: 'translate(-50%, -50%)' });
    expect(overlayPositionStyle({ x: 100, y: 100 })).toEqual({ left: '100%', top: '100%', transform: 'translate(-100%, -100%)' });
  });
});

describe('snapPercent·dragPercent', () => {
  it('가장자리·가운데에 3% 안으로 가까우면 붙이고, 0~100 밖은 잘라낸다', () => {
    expect(snapPercent(2)).toBe(0);
    expect(snapPercent(48)).toBe(50);
    expect(snapPercent(98)).toBe(100);
    expect(snapPercent(30)).toBe(30);
    expect(snapPercent(-20)).toBe(0);
    expect(snapPercent(140)).toBe(100);
  });

  it('끈 거리를 남는 공간에 대한 비율로 바꾼다', () => {
    // 남는 공간 500px에서 오른쪽으로 100px 끌면 20% 이동
    expect(dragPercent(0, 100, 500)).toBe(20);
    expect(dragPercent(50, -100, 500)).toBe(30);
    // 끝을 넘겨 끌어도 끝에서 멈춘다
    expect(dragPercent(80, 400, 500)).toBe(100);
    // 남는 공간이 없으면 그대로
    expect(dragPercent(40, 100, 0)).toBe(40);
  });
});

describe('keyboardMove', () => {
  it('화살표는 2%, Shift와 함께면 10% 옮기고 끝을 넘지 않는다', () => {
    expect(keyboardMove({ x: 50, y: 50 }, 'ArrowRight', false)).toEqual({ x: 52, y: 50 });
    expect(keyboardMove({ x: 50, y: 50 }, 'ArrowUp', true)).toEqual({ x: 50, y: 40 });
    expect(keyboardMove({ x: 1, y: 99 }, 'ArrowLeft', false)).toEqual({ x: 0, y: 99 });
    expect(keyboardMove({ x: 50, y: 99 }, 'ArrowDown', true)).toEqual({ x: 50, y: 100 });
    expect(keyboardMove({ x: 50, y: 50 }, 'Enter', false)).toBeUndefined();
  });
});

describe('sameLayout', () => {
  it('네 위치가 모두 같아야 같은 배치', () => {
    expect(sameLayout(DEFAULT_OVERLAY_LAYOUT, { ...DEFAULT_OVERLAY_LAYOUT })).toBe(true);
    expect(sameLayout(DEFAULT_OVERLAY_LAYOUT, { ...DEFAULT_OVERLAY_LAYOUT, bid: { x: 50, y: 2 } })).toBe(false);
  });
});

describe('숨김·박스', () => {
  const saved = {
    featured: { x: 0, y: 100, hidden: true },
    donation: { x: 100, y: 0 },
    bid: { x: 50, y: 0 },
    sold: { x: 50, y: 50 },
    boxes: [
      { type: 'TEXT' as const, x: 50, y: 10, text: '오늘 마감 특가', textStyle: 'HIGHLIGHT' as const, textSize: 'LARGE' as const },
      { type: 'IMAGE' as const, x: 100, y: 0, imageKey: 'overlay-images/1/a.png', imageUrl: 'https://cdn/a.png', width: 30 },
      // 글자 없는 텍스트 박스처럼 모양이 맞지 않는 박스는 그리지 않는다.
      { type: 'TEXT' as const, x: 0, y: 0 },
    ],
  };

  it('숨긴 안내와 박스를 읽고, 저장 요청에는 화면용 id·주소를 빼고 보낸다', () => {
    const layout = resolveOverlayLayout(saved);
    expect(layout.featured).toEqual({ x: 0, y: 100, hidden: true });
    expect(layout.donation).toEqual({ x: 100, y: 0 });
    expect(layout.boxes).toHaveLength(2);

    expect(toOverlayLayoutRequest(layout)).toEqual({
      featured: { x: 0, y: 100, hidden: true },
      donation: { x: 100, y: 0, hidden: false },
      bid: { x: 50, y: 0, hidden: false },
      sold: { x: 50, y: 50, hidden: false },
      boxes: [
        { type: 'TEXT', x: 50, y: 10, text: '오늘 마감 특가', textStyle: 'HIGHLIGHT', textSize: 'LARGE' },
        { type: 'IMAGE', x: 100, y: 0, imageKey: 'overlay-images/1/a.png', width: 30 },
      ],
    });
  });

  it('박스의 화면용 id가 달라도 같은 배치로 본다. 숨김이 바뀌면 다른 배치다', () => {
    const a = resolveOverlayLayout(saved);
    const b = { ...a, boxes: a.boxes.map((box) => ({ ...box, id: 'other' })) };
    expect(sameLayout(a, b)).toBe(true);
    expect(sameLayout(a, { ...a, featured: { x: 0, y: 100 } })).toBe(false);
  });
});
