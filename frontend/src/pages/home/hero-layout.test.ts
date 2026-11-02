import { describe, expect, it } from 'vitest';
import { fitSideSlots, HERO_GAP, heroHeight, sideColumnWidth } from './hero-layout';

describe('sideColumnWidth', () => {
  it('큰 화면(16:9) 높이와 오른쪽 열 높이가 같아지는 너비를 준다', () => {
    for (const slots of [2, 3, 4]) {
      const width = 1200;
      const side = sideColumnWidth(width, slots);
      const mainWidth = width - HERO_GAP - side;
      expect((mainWidth * 9) / 16).toBeCloseTo(heroHeight(width, slots));
    }
  });
});

describe('fitSideSlots', () => {
  it('좁은 화면(1280 너비 창)은 2개', () => {
    expect(fitSideSlots(976, 720)).toBe(2);
  });

  it('넓고 높은 화면은 4개', () => {
    expect(fitSideSlots(1360, 1000)).toBe(4);
  });

  it('넓어도 창이 낮으면 줄인다', () => {
    expect(fitSideSlots(1360, 600)).toBeLessThan(4);
  });

  it('아무 조건도 안 맞으면 2개', () => {
    expect(fitSideSlots(400, 300)).toBe(2);
  });
});
