import { useEffect, useState, type RefObject } from 'react';

/** 큰 라이브 화면과 오른쪽 인기 방송 열 사이, 인기 방송 카드끼리의 간격(px). `gap-3`과 같다. */
export const HERO_GAP = 12;
/** 오른쪽 인기 방송 카드 수 범위 */
export const MIN_SIDE_SLOTS = 2;
export const MAX_SIDE_SLOTS = 4;
/** 인기 방송 카드가 이보다 좁아지면 글자가 잘려 읽기 어려우므로 카드 수를 줄인다. */
const MIN_SIDE_WIDTH = 240;
/** 홈 상단 라이브 영역이 화면 높이의 이 비율을 넘지 않게 카드 수를 줄인다. (아래 섹션이 조금은 보이게) */
const MAX_HEIGHT_RATIO = 0.72;

/**
 * 카드 n개를 세로로 쌓은 오른쪽 열 너비. 큰 화면(16:9) 높이와 오른쪽 열(16:9 카드 n개 + 간격) 높이가 같아지는 값이다.
 *   큰 화면 너비 W = 전체 C - 간격 - 열 너비 w,  W·9/16 = n·w·9/16 + (n-1)·간격
 *   → w = (C - 간격 - (n-1)·간격·16/9) / (n+1)
 */
export function sideColumnWidth(containerWidth: number, slots: number): number {
  return Math.max(0, (containerWidth - HERO_GAP - ((slots - 1) * HERO_GAP * 16) / 9) / (slots + 1));
}

/** 오른쪽 열 높이(= 큰 화면 높이) */
export function heroHeight(containerWidth: number, slots: number): number {
  return (slots * sideColumnWidth(containerWidth, slots) * 9) / 16 + (slots - 1) * HERO_GAP;
}

/**
 * 화면 크기에 맞는 인기 방송 카드 수(2~4). 넓을수록(카드가 충분히 넓을 때) 높을수록(영역이 화면을 넘지 않을 때) 많이 보여 준다.
 * 아무 조건도 맞지 않으면 가장 적은 수(2)를 쓴다.
 */
export function fitSideSlots(containerWidth: number, viewportHeight: number): number {
  for (let slots = MAX_SIDE_SLOTS; slots > MIN_SIDE_SLOTS; slots--) {
    if (sideColumnWidth(containerWidth, slots) >= MIN_SIDE_WIDTH && heroHeight(containerWidth, slots) <= viewportHeight * MAX_HEIGHT_RATIO) {
      return slots;
    }
  }
  return MIN_SIDE_SLOTS;
}

/** 요소 너비(px)와 창 높이를 따라간다. 처음 그리기 전에는 너비가 0이다. */
export function useHeroSize(ref: RefObject<HTMLElement | null>) {
  const [width, setWidth] = useState(0);
  const [viewportHeight, setViewportHeight] = useState(() => window.innerHeight);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    setWidth(element.clientWidth);
    const observer = new ResizeObserver(() => setWidth(element.clientWidth));
    observer.observe(element);
    return () => observer.disconnect();
  }, [ref]);

  useEffect(() => {
    const onResize = () => setViewportHeight(window.innerHeight);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);

  return { width, viewportHeight };
}
