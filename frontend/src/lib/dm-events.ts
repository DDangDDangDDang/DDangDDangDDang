import { useEffect, useRef } from 'react';
import type { DmEvent } from '@/api';

/*
 * 1:1 채팅 실시간 이벤트를 화면 여러 곳(헤더 배지, 대화방 목록, 열린 대화)에 나눠 준다.
 * 연결은 DmRealtime(앱 전체에 하나)이 맡고, 화면은 useDmEvent로 받기만 한다.
 */
const listeners = new Set<(event: DmEvent) => void>();

export function emitDmEvent(event: DmEvent) {
  listeners.forEach((listener) => listener(event));
}

/** 1:1 채팅 이벤트를 받는다. 화면을 떠나면 자동으로 그만 받는다. */
export function useDmEvent(handler: (event: DmEvent) => void) {
  const handlerRef = useRef(handler);
  useEffect(() => {
    handlerRef.current = handler;
  });
  useEffect(() => {
    const listener = (event: DmEvent) => handlerRef.current(event);
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);
}
