/*
 * 소셜 로그인 (카카오, 구글). 백엔드: POST /api/v1/auth/oauth/{provider}, /{provider}/phone/code, /{provider}/signup
 *
 * 1. [카카오로 시작하기] / [Google로 계속하기] → 제공자 로그인 페이지 (socialAuthorizeUrl)
 * 2. 제공자가 /oauth/{provider}/callback?code=...&state=... 로 돌려보냄 (SocialCallbackPage)
 * 3. state를 확인하고(takeSocialState) 인가 코드를 백엔드로 보냄
 *
 * state: 우리가 시작한 로그인의 콜백인지 확인하는 무작위 값(CSRF 방지). 시작할 때 sessionStorage에 두고, 콜백에서 같은지 본다.
 * 로그인 뒤 돌아갈 주소(redirect)와 제공자도 같이 둔다. (카카오로 시작한 state를 구글 콜백에 쓰지 못하게)
 */

export const SOCIAL_PROVIDERS = ['kakao', 'google'] as const;
export type SocialProvider = (typeof SOCIAL_PROVIDERS)[number];

export const SOCIAL_PROVIDER_LABEL: Record<SocialProvider, string> = {
    kakao: '카카오',
    google: 'Google',
};

export function isSocialProvider(value: string | undefined): value is SocialProvider {
    return SOCIAL_PROVIDERS.includes(value as SocialProvider);
}

const STORAGE_KEY = 'ddang.social-login';

const AUTHORIZE: Record<SocialProvider, { url: string; scope?: string; envKey: string; envRedirect: string }> = {
    kakao: { url: 'https://kauth.kakao.com/oauth/authorize', envKey: 'VITE_KAKAO_REST_API_KEY', envRedirect: 'VITE_KAKAO_REDIRECT_URI' },
    google: {
        url: 'https://accounts.google.com/o/oauth2/v2/auth',
        scope: 'openid email profile',
        envKey: 'VITE_GOOGLE_CLIENT_ID',
        envRedirect: 'VITE_GOOGLE_REDIRECT_URI',
    },
};

export function socialCallbackPath(provider: SocialProvider): string {
    return `/oauth/${provider}/callback`;
}

export type SocialConfig = { clientId: string; redirectUri: string };

/**
 * 소셜 로그인 설정 (frontend/.env.local)
 * - 카카오: VITE_KAKAO_REST_API_KEY, VITE_KAKAO_REDIRECT_URI
 * - 구글: VITE_GOOGLE_CLIENT_ID, VITE_GOOGLE_REDIRECT_URI
 * 키(클라이언트 ID)는 로그인 페이지 주소에 그대로 보이는 공개 값이다. 없으면 undefined (버튼을 누르면 설정 안내)
 * redirect URI가 없으면 지금 주소의 /oauth/{provider}/callback. 백엔드 설정, 제공자 콘솔 등록값과 같아야 한다.
 */
export function socialConfig(
    provider: SocialProvider,
    env: Record<string, string | undefined> = import.meta.env,
    origin: string = window.location.origin,
): SocialConfig | undefined {
    const { envKey, envRedirect } = AUTHORIZE[provider];
    const clientId = env[envKey]?.trim();
    if (!clientId) return undefined;
    const redirectUri = env[envRedirect]?.trim() || `${origin}${socialCallbackPath(provider)}`;
    return { clientId, redirectUri };
}

/** 제공자 로그인 페이지 주소를 만들고, 콜백에서 확인할 state와 돌아갈 주소를 저장한다. */
export function socialAuthorizeUrl(provider: SocialProvider, config: SocialConfig, redirect: string): string {
    const state = randomState();
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ provider, state, redirect }));
    const { url, scope } = AUTHORIZE[provider];
    const params = new URLSearchParams({
        response_type: 'code',
        client_id: config.clientId,
        redirect_uri: config.redirectUri,
        state,
    });
    if (scope) params.set('scope', scope);
    return `${url}?${params.toString()}`;
}

/**
 * 콜백의 제공자·state가 시작할 때 저장한 값과 같으면 돌아갈 주소를, 다르거나 없으면 undefined.
 * 한 번 확인하면 지운다. (같은 콜백 주소로 다시 들어와도 재사용되지 않게)
 */
export function takeSocialState(provider: SocialProvider, state: string | null): { redirect: string } | undefined {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
    if (!saved || !state) return undefined;
    try {
        const parsed = JSON.parse(saved) as { provider?: string; state?: string; redirect?: string };
        return parsed.provider === provider && parsed.state === state ? { redirect: parsed.redirect ?? '/' } : undefined;
    } catch {
        return undefined;
    }
}

function randomState(): string {
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
}