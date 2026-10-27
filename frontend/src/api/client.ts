import createClient, { type Middleware } from 'openapi-fetch';
import { ApiError, networkError } from './errors';
import type { paths } from './generated/schema';
import { getAccessToken, getSessionVersion, setSession, type Session } from './session';

/*
 * 백엔드 API 클라이언트. 경로·파라미터·응답 타입은 모두 docs/api/openapi.json에서 생성된다.
 *
 *   const { data } = await call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } }));
 *
 * - Access Token을 자동으로 붙인다.
 * - 401이 오면 리프레시 토큰 쿠키로 한 번 재발급하고 원래 요청을 다시 보낸다.
 * - 실패 응답은 call()이 ApiError로 바꿔 던진다.
 */
export const api = createClient<paths>({
  // 같은 출처로 요청한다. (로컬은 Vite 프록시, 배포는 같은 주소에서 서비스)
  baseUrl: window.location.origin,
  credentials: 'same-origin',
  // 호출할 때마다 전역 fetch를 찾는다. (테스트에서 fetch를 바꿔 끼울 수 있게)
  fetch: (request) => globalThis.fetch(request),
});

const REISSUE_PATH = '/api/v1/auth/reissue';
/** 토큰 없이 호출하는 인증 API. 401이 와도 재발급을 시도하지 않는다. */
const NO_REFRESH_PATHS = ['/api/v1/auth/login', '/api/v1/auth/signup', REISSUE_PATH];

// 원래 요청을 다시 보내기 위해 본문을 읽기 전 복사본을 둔다. (요청 id → 복사본)
const pending = new Map<string, Request>();

// 동시에 여러 요청이 401을 받아도 재발급은 한 번만 한다.
let refreshing: Promise<string | null> | null = null;

/** 재발급 응답을 이 시간 안에 받지 못하면 실패로 보고 진행한다. (첫 화면이 멈추지 않게) */
const REISSUE_TIMEOUT_MS = 5000;

/**
 * 리프레시 토큰 쿠키로 새 Access Token을 받는다. 받지 못하면 null.
 * - 4xx(리프레시 토큰 없음·만료·무효): 로그아웃 상태로 만든다.
 * - 5xx·네트워크 오류·시간 초과: 일시적인 문제일 수 있으므로 지금 세션은 그대로 둔다.
 */
export function refreshAccessToken(): Promise<string | null> {
  refreshing ??= (async () => {
    // 재발급을 기다리는 사이 로그아웃·로그인이 일어나면 그 결과를 덮어쓰지 않는다.
    const startedVersion = getSessionVersion();
    const apply = (next: Session): boolean => {
      if (getSessionVersion() !== startedVersion) return false;
      setSession(next);
      return true;
    };
    try {
      const res = await fetch(REISSUE_PATH, {
        method: 'POST',
        credentials: 'same-origin',
        signal: AbortSignal.timeout(REISSUE_TIMEOUT_MS),
      });
      if (res.status >= 400 && res.status < 500) {
        apply(null);
        return null;
      }
      if (!res.ok) return null;

      const data = (await res.json())?.data;
      if (!data?.accessToken || !data.user) {
        apply(null);
        return null;
      }
      return apply({ accessToken: data.accessToken, user: data.user }) ? (data.accessToken as string) : null;
    } catch {
      return null;
    } finally {
      refreshing = null;
    }
  })();
  return refreshing;
}

const authMiddleware: Middleware = {
  onRequest({ request, id }) {
    const token = getAccessToken();
    // 값이 빈 Authorization 헤더(명세상 필수라 비워서 보내는 경우)도 토큰으로 채운다.
    if (token && !request.headers.get('Authorization')) {
      request.headers.set('Authorization', `Bearer ${token}`);
    }
    pending.set(id, request.clone());
    return request;
  },
  async onResponse({ request, response, id }) {
    const original = pending.get(id);
    pending.delete(id);

    const path = new URL(request.url, window.location.origin).pathname;
    if (response.status !== 401 || !original || NO_REFRESH_PATHS.includes(path)) {
      return response;
    }

    const token = await refreshAccessToken();
    if (!token) return response;

    const retry = new Request(original, { headers: new Headers(original.headers) });
    retry.headers.set('Authorization', `Bearer ${token}`);
    return fetch(retry);
  },
  onError({ error, id }) {
    pending.delete(id);
    return networkError(error);
  },
};

api.use(authMiddleware);

type ApiEnvelope<T> = { success?: boolean; data?: T; error?: { code?: string; message?: string } };
type FetchResult<T> = { data?: ApiEnvelope<T>; error?: unknown; response: Response };

/**
 * api.GET/POST/... 의 결과에서 data를 꺼낸다. 실패 응답이면 ApiError를 던진다.
 * 204처럼 본문이 없는 응답은 undefined를 돌려준다.
 */
export async function call<T>(request: Promise<FetchResult<T>>): Promise<T> {
  let result: FetchResult<T>;
  try {
    result = await request;
  } catch (error) {
    throw error instanceof ApiError ? error : networkError(error);
  }

  const { data, error, response } = result;
  if (response.ok && data?.success !== false) {
    return data?.data as T;
  }

  const body = (error ?? data) as ApiEnvelope<unknown> | undefined;
  throw new ApiError(body?.error?.code ?? `HTTP_${response.status}`, body?.error?.message ?? '', response.status);
}
