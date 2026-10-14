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

let reissuing: Promise<LoginResponse> | null = null;

export function reissue(): Promise<LoginResponse> {
  // 여러 요청이 동시에 만료되어도 재발급은 한 번만 한다. (Refresh Token은 재발급마다 바뀐다)
  if (!reissuing) {
    reissuing = request<LoginResponse>('POST', '/api/v1/auth/reissue')
      .then((res) => {
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
  setSession({ accessToken: res.accessToken, user: res.user });
  return res;
}

export function signup(email: string, password: string, nickname: string): Promise<unknown> {
  return request('POST', '/api/v1/auth/signup', { email, password, nickname });
}

export async function logout(): Promise<void> {
  try {
    await request('POST', '/api/v1/auth/logout', undefined, getAccessToken());
  } finally {
    setSession(null);
  }
}

export const liveStreamApi = {
  create: (title: string) => api<LiveStream>('POST', '/api/v1/live-streams', { title }),
  get: (id: number) => api<LiveStream>('GET', `/api/v1/live-streams/${id}`),
  start: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/start`),
  join: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/join`),
  end: (id: number) => api<LiveStream>('POST', `/api/v1/live-streams/${id}/end`),
};

export function errorText(e: unknown): string {
  if (e instanceof ApiError) return `[${e.code}] ${e.message}`;
  if (e instanceof Error) return e.message;
  return String(e);
}
