import { api, call, refreshAccessToken } from './client';
import type { components } from './generated/schema';
import { releasePushSubscription } from './push';
import { setSession, type UserInfo } from './session';

export type LoginRequest = components['schemas']['LoginRequest'];

/** 로그인. 리프레시 토큰은 서버가 쿠키로 내려준다. */
export async function login(body: LoginRequest) {
  const data = await call(api.POST('/api/v1/auth/login', { body }));
  if (data?.accessToken && data.user) {
    setSession({ accessToken: data.accessToken, user: data.user });
  }
  return data;
}

/** 로그아웃. 서버 요청이 실패해도 화면에서는 로그아웃 상태로 만든다. */
export async function logout() {
  try {
    // 토큰이 살아 있을 때 이 브라우저의 알림 구독부터 지운다.
    await releasePushSubscription();
    // Authorization 헤더는 클라이언트가 붙인다. (명세상 필수 파라미터라 빈 값으로 둔다)
    await call(api.POST('/api/v1/auth/logout', { params: { header: { Authorization: '' } } }));
  } finally {
    setSession(null);
  }
}

/**
 * 서버가 새 Access Token을 준 경우(닉네임 변경 등) 세션을 바꾼다.
 * 서버 응답의 회원 정보에서 세션에 필요한 값만 남긴다.
 */
export function replaceSession(accessToken: string, user: UserInfo) {
  setSession({ accessToken, user: { userId: user.userId, nickname: user.nickname, role: user.role } });
}

/** 서버 요청 없이 이 화면의 로그인 상태만 지운다. (비밀번호 변경처럼 서버가 이미 로그아웃시킨 경우) */
export function clearSession() {
  setSession(null);
}

/** 앱 시작 시 한 번 호출한다. 리프레시 토큰 쿠키가 살아 있으면 로그인 상태를 되살린다. */
export function restoreSession() {
  return refreshAccessToken();
}
