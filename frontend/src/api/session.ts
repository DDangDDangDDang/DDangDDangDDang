import { useSyncExternalStore } from 'react';
import type { components } from './generated/schema';

export type UserInfo = components['schemas']['UserInfo'];
export type Session = { accessToken: string; user: UserInfo } | null;

/*
 * 로그인 상태. Access Token은 XSS 노출을 줄이려고 메모리에만 둔다.
 * 새로고침하면 사라지므로 앱 시작 시 리프레시 토큰 쿠키로 재발급한다. (auth.ts의 restoreSession)
 */
let session: Session = null;
const listeners = new Set<() => void>();

export function getSession(): Session {
  return session;
}

export function getAccessToken(): string | null {
  return session?.accessToken ?? null;
}

export function setSession(next: Session) {
  session = next;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** 화면에서 로그인 상태를 읽는다. 로그인·로그아웃·재발급 때 다시 그려진다. */
export function useSession(): Session {
  return useSyncExternalStore(subscribe, getSession, getSession);
}
