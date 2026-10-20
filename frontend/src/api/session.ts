import { useSyncExternalStore } from 'react';
import type { components } from './generated/schema';

export type UserInfo = components['schemas']['UserInfo'];
export type Session = { accessToken: string; user: UserInfo } | null;

/*
 * 로그인 상태. Access Token은 XSS 노출을 줄이려고 메모리에만 둔다.
 * 새로고침하면 사라지므로 앱 시작 시 리프레시 토큰 쿠키로 재발급한다. (auth.ts의 restoreSession)
 */
let session: Session = null;
// 세션이 바뀔 때마다 1씩 오른다. 오래 걸린 재발급 결과가 그 사이의 로그아웃을 덮어쓰지 않게 하는 데 쓴다.
let version = 0;
const listeners = new Set<() => void>();

export function getSession(): Session {
  return session;
}

export function getSessionVersion(): number {
  return version;
}

export function getAccessToken(): string | null {
  return session?.accessToken ?? null;
}

export function setSession(next: Session) {
  version += 1;
  session = next;
  listeners.forEach((listener) => listener());
}

/** 세션이 바뀔 때마다 호출된다. 구독을 끊는 함수를 돌려준다. */
export function subscribeSession(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** 화면에서 로그인 상태를 읽는다. 로그인·로그아웃·재발급 때 다시 그려진다. */
export function useSession(): Session {
  return useSyncExternalStore(subscribeSession, getSession, getSession);
}
