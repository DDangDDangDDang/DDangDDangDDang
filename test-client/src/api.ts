// 백엔드 공통 응답: { success, data, error: { code, message } }
type ApiEnvelope<T> = {
  success: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
};

export class ApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
  ) {
    super(message);
  }
}

export type UserInfo = { userId: number; nickname: string; role: string };
export type LoginResponse = { accessToken: string; expiresIn: number; user: UserInfo };

export type LiveStreamStatus = 'SCHEDULED' | 'LIVE' | 'ENDED';
export type LiveStream = {
  id: number;
  sellerId: number;
  title: string;
  status: LiveStreamStatus;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
};
export type LiveStreamToken = {
  liveStreamId: number;
  roomName: string;
  serverUrl: string;
  token: string;
};

export type Session = { accessToken: string; user: UserInfo } | null;

// Access Token은 XSS 노출을 줄이려고 메모리에만 둔다. 새로고침하면 Refresh Token 쿠키로 재발급한다.
let session: Session = null;
const sessionListeners = new Set<(session: Session) => void>();

export function getAccessToken(): string | null {
  return session?.accessToken ?? null;
}

function setSession(next: Session) {
  session = next;
  sessionListeners.forEach((listener) => listener(next));
}

export function onSessionChange(listener: (session: Session) => void): () => void {
  sessionListeners.add(listener);
  return () => {
    sessionListeners.delete(listener);
  };
}

async function request<T>(method: string, path: string, body?: unknown, token?: string | null): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
  });

  const text = await res.text();
  let json: ApiEnvelope<T> | null = null;
  try {
    json = text ? (JSON.parse(text) as ApiEnvelope<T>) : null;
  } catch {
    // 프록시 오류 페이지처럼 JSON이 아닌 응답
  }

  if (!res.ok || json?.success === false) {
    throw new ApiError(
      json?.error?.code ?? `HTTP_${res.status}`,
      json?.error?.message ?? (text || res.statusText),
      res.status,
    );
  }
  return (json?.data ?? null) as T;
}

// 로그인·로그아웃으로 세션이 바뀔 때마다 1씩 오른다.
// 재발급은 시작할 때의 세대를 기억했다가, 응답이 왔을 때 세대가 바뀌었으면 결과를 버린다.
// (로그아웃 뒤에 늦게 도착한 재발급 응답이 이전 사용자의 세션을 되살리지 않게 하기 위함)
let sessionGeneration = 0;

let reissuing: Promise<LoginResponse> | null = null;

// 로그아웃 요청이 서버에 가 있는 동안에는 재발급 요청을 보내지 않는다.
// 서버에서 재발급이 "기존 토큰 확인 → (로그아웃이 토큰 삭제) → 새 토큰 저장" 순서로 겹치면
// 로그아웃 뒤에 새 Refresh Token이 살아남아 새로고침 시 다시 로그인되기 때문이다.
let loggingOut = false;

export function reissue(): Promise<LoginResponse> {
  if (loggingOut) {
    return Promise.reject(new ApiError('SESSION_CHANGED', '로그아웃 중이라 재발급하지 않았어요.', 0));
  }
  // 여러 요청이 동시에 만료되어도 재발급은 한 번만 한다. (Refresh Token은 재발급마다 바뀐다)
  if (!reissuing) {
    const generation = sessionGeneration;
    reissuing = request<LoginResponse>('POST', '/api/v1/auth/reissue')
      .then((res) => {
        if (generation !== sessionGeneration) {
          throw new ApiError('SESSION_CHANGED', '로그인 상태가 바뀌어 재발급 결과를 버렸어요.', 0);
        }
        setSession({ accessToken: res.accessToken, user: res.user });
        return res;
      })
      .finally(() => {
        reissuing = null;
      });
  }
  return reissuing;
}

/** 인증이 필요한 API. Access Token이 만료(AUTH_002)되면 한 번 재발급하고 다시 요청한다. */
export async function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  try {
    return await request<T>(method, path, body, getAccessToken());
  } catch (e) {
    if (e instanceof ApiError && e.code === 'AUTH_002') {
      await reissue();
      return request<T>(method, path, body, getAccessToken());
    }
    throw e;
  }
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  const res = await request<LoginResponse>('POST', '/api/v1/auth/login', { email, password });
  sessionGeneration++;
  setSession({ accessToken: res.accessToken, user: res.user });
  return res;
}

export function signup(email: string, password: string, nickname: string): Promise<unknown> {
  return request('POST', '/api/v1/auth/signup', { email, password, nickname });
}

export async function logout(): Promise<void> {
  // 진행 중인 재발급이 있으면 끝난 뒤 최신 토큰으로 로그아웃한다.
  // 만료된 토큰으로 보내면 서버가 AUTH_002로 거부해 Refresh Token이 지워지지 않고, 새로고침하면 다시 로그인된다.
  if (reissuing) await reissuing.catch(() => undefined);
  try {
    // 만료 토큰이면 재발급 후 한 번 더 시도해야 하므로, 재발급을 막기 전에 토큰부터 확인한다.
    await logoutWithFreshToken();
  } finally {
    sessionGeneration++;
    setSession(null);
  }
}

async function logoutWithFreshToken(): Promise<void> {
  try {
    loggingOut = true;
    await request('POST', '/api/v1/auth/logout', undefined, getAccessToken());
  } catch (e) {
    if (!(e instanceof ApiError && e.code === 'AUTH_002')) throw e;
    // 로그아웃 요청이 끝났으니(거부됨) 재발급과 겹치지 않는다. 새 토큰으로 다시 로그아웃한다.
    loggingOut = false;
    await reissue();
    loggingOut = true;
    await request('POST', '/api/v1/auth/logout', undefined, getAccessToken());
  } finally {
    loggingOut = false;
  }
}

export const liveStreamApi = {
  create: (title: string) => api<LiveStream>('POST', '/api/v1/live-streams', { title }),
  get: (id: number) => api<LiveStream>('GET', `/api/v1/live-streams/${id}`),
  start: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/start`),
  join: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/join`),
  // 방송 중인 판매자가 다시 송출할 때 (새로고침, 송출 연결 끊김, 토큰 만료)
  publisherToken: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/publisher-token`),
  end: (id: number) => api<LiveStream>('POST', `/api/v1/live-streams/${id}/end`),
};

export function errorText(e: unknown): string {
  if (e instanceof ApiError) return `[${e.code}] ${e.message}`;
  if (e instanceof Error) return e.message;
  return String(e);
}
