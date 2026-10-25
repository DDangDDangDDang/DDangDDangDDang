import {api, call, refreshAccessToken} from './client';
import type {components} from './generated/schema';
import {releasePushSubscription} from './push';
import {setSession, type UserInfo} from './session';

export type LoginRequest = components['schemas']['LoginRequest'];
export type KakaoSignupRequest = components['schemas']['KakaoSignupRequest'];

/** 로그인. 리프레시 토큰은 서버가 쿠키로 내려준다. */
export async function login(body: LoginRequest) {
    const data = await call(api.POST('/api/v1/auth/login', {body}));
    if (data?.accessToken && data.user) {
        setSession({accessToken: data.accessToken, user: data.user});
    }
    return data;
}

/**
 * 카카오 로그인. 인가 코드를 보내고, 연결된 회원이면(status: LOGIN) 로그인 상태로 만든다.
 * 처음 온 사용자면(status: SIGNUP_REQUIRED) 가입 대기 토큰과 카카오 이메일·닉네임을 돌려준다.
 */
export async function kakaoLogin(code: string) {
    const data = await call(api.POST('/api/v1/auth/oauth/kakao', {body: {code}}));
    if (data?.status === 'LOGIN' && data.accessToken && data.user) {
        setSession({accessToken: data.accessToken, user: data.user});
    }
    return data;
}

/** 카카오 가입(또는 같은 휴대폰 번호의 기존 계정에 연결). 가입과 동시에 로그인한다. */
export async function kakaoSignup(body: KakaoSignupRequest) {
    const data = await call(api.POST('/api/v1/auth/oauth/kakao/signup', {body}));
    if (data?.accessToken && data.user) {
        setSession({accessToken: data.accessToken, user: data.user});
    }
    return data;
}

/** 로그아웃. 서버 요청이 실패해도 화면에서는 로그아웃 상태로 만든다. */
export async function logout() {
    try {
        // 토큰이 살아 있을 때 이 브라우저의 알림 구독부터 지운다.
        await releasePushSubscription();
        // Authorization 헤더는 클라이언트가 붙인다. (명세상 필수 파라미터라 빈 값으로 둔다)
        await call(api.POST('/api/v1/auth/logout', {params: {header: {Authorization: ''}}}));
    } finally {
        setSession(null);
    }
}

/**
 * 서버가 새 Access Token을 준 경우(닉네임 변경 등) 세션을 바꾼다.
 * 서버 응답의 회원 정보에서 세션에 필요한 값만 남긴다.
 */
export function replaceSession(accessToken: string, user: UserInfo) {
    setSession({accessToken, user: {userId: user.userId, nickname: user.nickname, role: user.role}});
}

/** 서버 요청 없이 이 화면의 로그인 상태만 지운다. (비밀번호 변경처럼 서버가 이미 로그아웃시킨 경우) */
export function clearSession() {
    setSession(null);
}

/** 앱 시작 시 한 번 호출한다. 리프레시 토큰 쿠키가 살아 있으면 로그인 상태를 되살린다. */
export function restoreSession() {
    return refreshAccessToken();
}
