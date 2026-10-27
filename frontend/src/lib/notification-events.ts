import { useEffect, useRef } from 'react';
import type { NotificationMessage } from '@/api';

/*
 * 실시간 알림(/user/queue/notifications)을 화면(구석 알림 팝업 등)에 나눠 준다.
 * 연결은 DmRealtime(앱 전체에 하나)이 맡고, 화면은 useNotificationEvent로 받기만 한다.
 */
/** 알림함 쿼리 (목록·안 읽은 수). 새 알림이 오면 다시 불러온다. */
export const NOTIFICATIONS_KEY = ['notifications'] as const;

const listeners = new Set<(notification: NotificationMessage) => void>();

export function emitNotification(notification: NotificationMessage) {
  listeners.forEach((listener) => listener(notification));
}

/** 새 알림을 받는다. 화면을 떠나면 자동으로 그만 받는다. */
export function useNotificationEvent(handler: (notification: NotificationMessage) => void) {
  const handlerRef = useRef(handler);
  useEffect(() => {
    handlerRef.current = handler;
  });
  useEffect(() => {
    const listener = (notification: NotificationMessage) => handlerRef.current(notification);
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  }, []);
}
