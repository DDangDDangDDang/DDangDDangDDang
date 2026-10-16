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
  description: string | null;
  status: LiveStreamStatus;
  createdAt: string;
  // 판매자가 정한 시작 예정 시간 (없으면 null). startedAt은 실제 시작 시간
  scheduledStartAt: string | null;
  startedAt: string | null;
  endedAt: string | null;
};
// scheduledStartAt은 방송을 시작하기 전(대기 중)에만 바꿀 수 있다.
export type LiveStreamUpdate = { title?: string; description?: string; scheduledStartAt?: string };

// 홈 화면 방송 카드. viewerIncrease: 최근 5분 동안 늘어난 시청자 수
export type LiveStreamCard = {
  liveStreamId: number;
  title: string;
  sellerId: number;
  sellerNickname: string | null;
  scheduledStartAt: string | null;
  startedAt: string | null;
  viewerCount: number;
  viewerIncrease: number;
};
// upcoming: 방송 예정 (시작 예정 시간이 가까운 순)
export type LiveStreamHome = { mostViewed: LiveStreamCard[]; surging: LiveStreamCard[]; upcoming: LiveStreamCard[] };

/** "2026-10-20T20:00:00" → "10월 20일 (화) 20:00" */
export function scheduleText(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'long', day: 'numeric', weekday: 'short', hour: '2-digit', minute: '2-digit', hour12: false,
  });
}

/** 서버 시간("2026-10-20T20:00:00")을 datetime-local 입력값("2026-10-20T20:00")으로 */
export function toDateTimeInput(iso: string | null): string {
  return iso ? iso.slice(0, 16) : '';
}

// /topic/live-streams/{id} 로 오는 방송 정보 변경 알림. 바뀐 값만이 아니라 방송 전체 정보가 온다.
export type LiveStreamUpdatedMessage = { type: 'UPDATED'; liveStream: LiveStream };

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
  // scheduledStartAt: datetime-local 입력값("2026-10-20T20:00"). 비우면 바로 시작하는 방송
  create: (title: string, description?: string, scheduledStartAt?: string) =>
    api<LiveStream>('POST', '/api/v1/live-streams', {
      title,
      description: description || undefined,
      scheduledStartAt: scheduledStartAt || undefined,
    }),
  get: (id: number) => api<LiveStream>('GET', `/api/v1/live-streams/${id}`),
  home: (size = 10) => api<LiveStreamHome>('GET', `/api/v1/live-streams/home?size=${size}`),
  // 내 종료되지 않은 방송 (판매자당 하나). 없으면 null
  myActive: () => api<LiveStream | null>('GET', '/api/v1/live-streams/me/active'),
  // 보낸 값만 바뀐다. description을 ''로 보내면 설명을 지운다.
  update: (id: number, changes: LiveStreamUpdate) => api<LiveStream>('PATCH', `/api/v1/live-streams/${id}`, changes),
  start: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/start`),
  join: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/join`),
  // 방송 중인 판매자가 다시 송출할 때 (새로고침, 송출 연결 끊김, 토큰 만료)
  publisherToken: (id: number) => api<LiveStreamToken>('POST', `/api/v1/live-streams/${id}/publisher-token`),
  end: (id: number) => api<LiveStream>('POST', `/api/v1/live-streams/${id}/end`),
};

// ---- 1:1 채팅 ----
export type CursorPage<T> = { content: T[]; nextCursor: number | null; hasNext: boolean };
export type DmRoom = {
  roomId: number;
  otherUser: { userId: number; nickname: string | null };
  lastMessageId: number | null;
  lastMessagePreview: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
  // 상대가 마지막으로 읽은 메시지. 내 메시지 중 이보다 id가 크면 상대가 아직 안 읽은 것("1")
  otherLastReadMessageId: number | null;
  blockedByMe: boolean;
};
export type DmMessage = {
  messageId: number;
  roomId: number;
  senderId: number;
  clientMessageId: string;
  content: string;
  sentAt: string;
};
// /user/queue/dm 으로 오는 실시간 이벤트
export type DmEvent =
  | { type: 'MESSAGE'; roomId: number; message: DmMessage; readerId: null; lastReadMessageId: null }
  | { type: 'READ'; roomId: number; message: null; readerId: number; lastReadMessageId: number };
export type DmReportReason = 'EXTERNAL_TRADE' | 'SCAM' | 'HARASSMENT' | 'SEXUAL' | 'SPAM' | 'OTHER';
export type DmBlock = { userId: number; nickname: string | null; blockedAt: string };

function cursorQuery(cursor: number | null, size: number): string {
  return `?size=${size}${cursor ? `&cursor=${cursor}` : ''}`;
}

export const dmApi = {
  openRoom: (targetUserId: number) => api<DmRoom>('POST', '/api/v1/dm/rooms', { targetUserId }),
  rooms: (cursor: number | null = null, size = 20) => api<CursorPage<DmRoom>>('GET', `/api/v1/dm/rooms${cursorQuery(cursor, size)}`),
  unreadCount: () => api<{ unreadCount: number }>('GET', '/api/v1/dm/unread-count'),
  messages: (roomId: number, cursor: number | null = null, size = 30) =>
    api<CursorPage<DmMessage>>('GET', `/api/v1/dm/rooms/${roomId}/messages${cursorQuery(cursor, size)}`),
  // clientMessageId: 재전송해도 한 번만 저장되도록 메시지마다 만드는 ID
  send: (roomId: number, clientMessageId: string, content: string) =>
    api<DmMessage>('POST', `/api/v1/dm/rooms/${roomId}/messages`, { clientMessageId, content }),
  read: (roomId: number, lastReadMessageId?: number) =>
    api<{ roomId: number; lastReadMessageId: number | null; unreadCount: number }>(
      'POST', `/api/v1/dm/rooms/${roomId}/read`, lastReadMessageId ? { lastReadMessageId } : undefined),
  block: (userId: number) => api<DmBlock>('POST', '/api/v1/dm/blocks', { userId }),
  unblock: (userId: number) => api<null>('DELETE', `/api/v1/dm/blocks/${userId}`),
  report: (roomId: number, reason: DmReportReason, messageId?: number, detail?: string) =>
    api<unknown>('POST', `/api/v1/dm/rooms/${roomId}/reports`, { reason, messageId, detail: detail || undefined }),
};

export function errorText(e: unknown): string {
  if (e instanceof ApiError) return `[${e.code}] ${e.message}`;
  if (e instanceof Error) return e.message;
  return String(e);
}
