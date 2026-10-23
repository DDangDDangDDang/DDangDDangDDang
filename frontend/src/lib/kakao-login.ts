/*
 * 카카오 로그인 (백엔드: POST /api/v1/auth/oauth/kakao, /oauth/kakao/signup)
 *
 * 1. [카카오로 시작하기] → 카카오 로그인 페이지 (kakaoAuthorizeUrl)
 * 2. 카카오가 /oauth/kakao/callback?code=...&state=... 로 돌려보냄 (KakaoCallbackPage)
 * 3. state를 확인하고(takeKakaoState) 인가 코드를 백엔드로 보냄
 *
 * state: 우리가 시작한 로그인의 콜백인지 확인하는 무작위 값(CSRF 방지). 시작할 때 sessionStorage에 두고, 콜백에서 같은지 본다.
 * 로그인 뒤 돌아갈 주소(redirect)도 같이 둔다.
 */

const STORAGE_KEY = 'ddang.kakao-login';
const AUTHORIZE_URL = 'https://kauth.kakao.com/oauth/authorize';
export const KAKAO_CALLBACK_PATH = '/oauth/kakao/callback';

export type KakaoConfig = { restApiKey: string; redirectUri: string };

/**
 * 카카오 로그인 설정 (frontend/.env.local의 VITE_KAKAO_REST_API_KEY, VITE_KAKAO_REDIRECT_URI).
 * REST API 키는 카카오 로그인 페이지 주소에 그대로 보이는 공개 값이다. 없으면 undefined (버튼을 누르면 설정 안내)
 * redirect URI가 없으면 지금 주소의 /oauth/kakao/callback. 백엔드 KAKAO_REDIRECT_URI, 카카오 콘솔 등록값과 같아야 한다.
 */
export function kakaoConfig(
    env: Record<string, string | undefined> = import.meta.env,
    origin: string = window.location.origin,
): KakaoConfig | undefined {
    const restApiKey = env.VITE_KAKAO_REST_API_KEY?.trim();
    if (!restApiKey) return undefined;
    const redirectUri = env.VITE_KAKAO_REDIRECT_URI?.trim() || `${origin}${KAKAO_CALLBACK_PATH}`;
    return { restApiKey, redirectUri };
}

/** 카카오 로그인 페이지 주소를 만들고, 콜백에서 확인할 state와 돌아갈 주소를 저장한다. */
export function kakaoAuthorizeUrl(config: KakaoConfig, redirect: string): string {
    const state = randomState();
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ state, redirect }));
    const params = new URLSearchParams({
        response_type: 'code',
        client_id: config.restApiKey,
        redirect_uri: config.redirectUri,
        state,
    });
    return `${AUTHORIZE_URL}?${params.toString()}`;
}

/**
 * 콜백의 state가 시작할 때 저장한 값과 같으면 돌아갈 주소를, 다르거나 없으면 undefined.
 * 한 번 확인하면 지운다. (같은 콜백 주소로 다시 들어와도 재사용되지 않게)
 */
export function takeKakaoState(state: string | null): { redirect: string } | undefined {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    if (!saved || !state) return undefined;
    try {
        const parsed = JSON.parse(saved) as { state?: string; redirect?: string };
        return parsed.state === state ? { redirect: parsed.redirect ?? '/' } : undefined;
    } catch {
        return undefined;
    }
}

function randomState(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}