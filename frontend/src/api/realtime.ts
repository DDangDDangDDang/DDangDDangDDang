import { Client, type StompSubscription } from '@stomp/stompjs';
import { refreshAccessToken } from './client';
import type { ErrorCode } from './generated/error-codes';
import type { components } from './generated/schema';
import { getAccessToken } from './session';

/*
 * 실시간 메시지(STOMP over WebSocket). 경로와 메시지 형식은 docs/api/README.md의 "실시간 메시지"를 따른다.
 * 이 부분은 명세에서 자동 생성되지 않으므로, 문서가 바뀌면 아래 타입과 경로도 같이 고친다.
 *
 *   const conn = connectRealtime(setStatus);
 *   conn.subscribe<ChatMessage>(REALTIME.liveChat(id), (message) => ...);
 *   conn.publish(REALTIME.sendLiveChat(id), { content });
 *   conn.close();
 *
 * - CONNECT 프레임에 Access Token을 붙인다. 화면에서 토큰을 다루지 않는다.
 * - 끊기면 자동으로 다시 연결하고, 구독도 다시 한다.
 * - 서버가 ERROR 프레임으로 끊으면(토큰 만료 등) 다음 연결 전에 토큰을 재발급한다.
 */

/** 방송 채팅 (`/topic/live-streams/{id}/chat`). SYSTEM은 서버 안내(방송 종료 등)로 senderId, nickname이 null */
export type ChatMessage = {
  messageId: string;
  type: 'CHAT' | 'SYSTEM';
  liveStreamId: number;
  senderId: number | null;
  nickname: string | null;
  content: string;
  sentAt: string;
};

/** 방송 정보 변경 (`/topic/live-streams/{id}`). 바뀐 값만이 아니라 전체 정보 */
export type LiveStreamUpdatedMessage = {
  type: 'UPDATED';
  liveStream: components['schemas']['LiveStreamResponse'];
};

/** AI가 확인이 필요하다고 본 채팅 (`/user/queue/moderation`). 방송 판매자에게만 온다. */
export type ModerationAlert = {
  liveStreamId: number;
  /** 문제의 채팅 messageId */
  messageId: string;
  senderId: number;
  nickname: string;
  content: string;
  category: 'EXTERNAL_TRADE' | 'SCAM' | 'HARASSMENT' | 'HATE' | 'SEXUAL' | 'SPAM' | 'PERSONAL_INFO';
  /** 화면에 보여줄 분류 이름 */
  categoryLabel: string;
  /** 판단 이유 */
  reason: string;
  detectedAt: string;
};

/**
 * 1:1 채팅 이벤트 (`/user/queue/dm`). 내가 보낸 메시지도 온다. (같은 계정의 다른 탭·기기)
 * MESSAGE: message가 새 메시지 · READ: readerId가 lastReadMessageId까지 읽음 (보낸 사람 화면의 "1"을 지운다)
 */
export type DmEvent = {
  type: 'MESSAGE' | 'READ';
  roomId: number;
  message: components['schemas']['DmMessageResponse'] | null;
  readerId: number | null;
  lastReadMessageId: number | null;
};

/** 나에게만 오는 오류 (`/user/queue/errors`). 채팅 전송 실패 등 */
export type RealtimeError = components['schemas']['ErrorResponse'];

export const REALTIME = {
  liveChat: (liveStreamId: number) => `/topic/live-streams/${liveStreamId}/chat`,
  liveStream: (liveStreamId: number) => `/topic/live-streams/${liveStreamId}`,
  errors: '/user/queue/errors',
  moderation: '/user/queue/moderation',
  dm: '/user/queue/dm',
  sendLiveChat: (liveStreamId: number) => `/app/live-streams/${liveStreamId}/chat`,
} as const;

/** connecting: 처음 연결 중 · connected: 연결됨 · reconnecting: 끊겨서 다시 연결 중 */
export type RealtimeStatus = 'connecting' | 'connected' | 'reconnecting';

export type RealtimeConnection = {
  /** 구독한다. 다시 연결되면 자동으로 다시 구독한다. 구독을 끊는 함수를 돌려준다. */
  subscribe<T>(destination: string, handler: (body: T) => void): () => void;
  /** 보낸다. 연결돼 있지 않으면 보내지 않고 false를 돌려준다. */
  publish(destination: string, body: unknown): boolean;
  close(): void;
};

const RECONNECT_DELAY_MS = 3000;

/** 구독·전송 경로가 허용되지 않음 (StompAuthChannelInterceptor) */
const INVALID_DESTINATION: ErrorCode = 'COMMON_005';

function brokerUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  // 같은 주소로 연결한다. (로컬은 Vite 프록시가 /ws를 백엔드로 넘김)
  return `${protocol}://${window.location.host}/ws`;
}

/**
 * guest: 로그인하지 않은 사람의 연결. 토큰 없이 연결해 방송 토픽(채팅·방송 정보)만 구독할 수 있다. (보내기·/user/queue는 서버가 거부)
 */
export function connectRealtime(onStatus: (status: RealtimeStatus) => void, options: { guest?: boolean } = {}): RealtimeConnection {
  type Entry = { destination: string; handler: (body: unknown) => void; subscription?: StompSubscription };
  const entries = new Set<Entry>();
  let connectedOnce = false;
  // 서버가 연결을 거부·종료했으면(토큰 만료, 로그아웃된 토큰) 다음 연결 전에 토큰을 새로 받는다.
  let refreshBeforeConnect = false;

  const client = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: RECONNECT_DELAY_MS,
    beforeConnect: async () => {
      if (options.guest) {
        client.connectHeaders = {};
        return;
      }
      let token = getAccessToken();
      if (!token || refreshBeforeConnect) {
        // 재발급이 일시적으로 실패하면(서버 오류) 지금 토큰으로 시도한다. 재발급 4xx면 세션이 지워져 null이 된다.
        token = (await refreshAccessToken()) ?? getAccessToken();
        refreshBeforeConnect = false;
      }
      if (!token) {
        // 로그인 상태가 아니면 연결하지 않는다. (로그인 화면으로 이동하면서 연결도 닫힌다)
        await client.deactivate();
        return;
      }
      client.connectHeaders = { Authorization: `Bearer ${token}` };
    },
    onConnect: () => {
      connectedOnce = true;
      entries.forEach(subscribeEntry);
      onStatus('connected');
    },
    // 서버는 ERROR 프레임의 message 헤더에 에러 코드를 담는다. (StompErrorHandler)
    onStompError: (frame) => {
      const code = frame.headers.message ?? '';
      if (code === INVALID_DESTINATION) {
        // 허용되지 않은 경로는 다시 연결해도 같으므로 멈춘다. (REALTIME 경로가 서버와 어긋난 코드 오류)
        console.error('[realtime] 서버가 허용하지 않는 경로입니다. REALTIME 경로를 docs/api/README.md와 맞춰 주세요.');
        void client.deactivate();
        return;
      }
      // 인증 오류(만료·로그아웃된 토큰 등)면 다음 연결 전에 토큰을 재발급한다. 그 밖의 오류(서버 오류)는 그대로 다시 연결한다.
      if (code.startsWith('AUTH_')) refreshBeforeConnect = true;
      // 서버는 ERROR 프레임 뒤에 연결을 닫지만, 닫히지 않는 경우에도 다시 연결되도록 직접 끊는다.
      client.forceDisconnect();
    },
    onWebSocketClose: () => {
      entries.forEach((entry) => (entry.subscription = undefined));
      if (client.active) onStatus(connectedOnce ? 'reconnecting' : 'connecting');
    },
  });

  function subscribeEntry(entry: Entry) {
    entry.subscription = client.subscribe(entry.destination, (frame) => {
      let body: unknown;
      try {
        body = JSON.parse(frame.body);
      } catch {
        return; // 형식이 맞지 않는 메시지는 무시한다.
      }
      entry.handler(body);
    });
  }

  onStatus('connecting');
  client.activate();

  return {
    subscribe<T>(destination: string, handler: (body: T) => void) {
      const entry: Entry = { destination, handler: handler as (body: unknown) => void };
      entries.add(entry);
      if (client.connected) subscribeEntry(entry);
      return () => {
        entries.delete(entry);
        if (client.connected) entry.subscription?.unsubscribe();
      };
    },
    publish(destination, body) {
      if (!client.connected) return false;
      client.publish({ destination, body: JSON.stringify(body) });
      return true;
    },
    close() {
      entries.clear();
      void client.deactivate();
    },
  };
}
