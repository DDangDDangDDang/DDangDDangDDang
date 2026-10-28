import { useCallback, useEffect, useRef, useState } from 'react';

/** 방송 카드 미리보기를 시작하기까지 마우스를 올려 두어야 하는 시간. 스쳐 지나가는 카드는 연결하지 않는다. */
export const HOVER_INTENT_MS = 400;

/**
 * 마우스를 delay만큼 올려 두었는지. 그 전에 떠나면 false 그대로이고, 떠나는 즉시 false가 된다.
 * 돌려준 onMouseEnter·onMouseLeave를 요소에 붙인다.
 */
export function useHoverIntent(delay = HOVER_INTENT_MS) {
  const [hovered, setHovered] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  const onMouseEnter = useCallback(() => {
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => setHovered(true), delay);
  }, [delay]);

  const onMouseLeave = useCallback(() => {
    clearTimeout(timerRef.current);
    setHovered(false);
  }, []);

  useEffect(() => () => clearTimeout(timerRef.current), []);

  return { hovered, onMouseEnter, onMouseLeave };
}
