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

/**
 * 방송 채팅 (`/topic/live-streams/{id}/chat`). SYSTEM은 서버 안내(방송 종료 등)로 senderId, nickname이 null
 * DONATION은 도네이션 마일리지 선물: 선물한 사람(senderId·nickname), 마일리지(amount), 함께 보낸 한마디(content, 없으면 null)
 * BID는 방송에 올라간 경매의 입찰: 입찰자(senderId·nickname), 입찰 금액(amount), 직전 현재가(previousAmount), 경매(auctionId·productName)
 * AUCTION_SOLD는 낙찰: 낙찰자(senderId·nickname), 낙찰가(amount), 경매(auctionId·productName)
 * BID·AUCTION_SOLD의 content는 채팅에 그대로 보여 줄 안내 문장이다.
 * CHAT의 badge는 보낸 사람이 장착한 닉네임 뱃지 (없으면 null)
 * MESSAGE_HIDDEN·MESSAGE_RESTORED는 새 줄이 아니라, targetMessageId 채팅(또는 선물 한마디)을 AI 검토로 가리거나 판매자가 되돌린 것이다.
 */
export type ChatMessage = {
  messageId: string;
  type: 'CHAT' | 'SYSTEM' | 'DONATION' | 'BID' | 'AUCTION_SOLD' | 'MESSAGE_HIDDEN' | 'MESSAGE_RESTORED';
  liveStreamId: number;
  senderId: number | null;
  nickname: string | null;
  content: string | null;
  sentAt: string;
  /** DONATION·BID·AUCTION_SOLD에만 있다 */
  amount?: number | null;
  /** BID에만 있다 (첫 입찰이면 시작가) */
  previousAmount?: number | null;
  /** BID·AUCTION_SOLD에만 있다 */
  auctionId?: number | null;
  productName?: string | null;
  /** CHAT에만 있다. 보낸 사람이 장착한 닉네임 뱃지 */
  badge?: 'ANGEL_WINGS' | 'DEVIL_WINGS' | 'HALO' | 'STAR' | null;
  /** MESSAGE_HIDDEN·MESSAGE_RESTORED에만 있다 */
  targetMessageId?: string | null;
  /** 서버가 보내지 않는 화면 전용 값: AI 검토로 가려진 메시지인지 (applyChatMessage가 채운다) */
  hidden?: boolean;
};

/** 방송 정보 변경 (`/topic/live-streams/{id}`). 바뀐 값만이 아니라 전체 정보 */
export type LiveStreamUpdatedMessage = {
  type: 'UPDATED';
  liveStream: components['schemas']['LiveStreamResponse'];
};

/** 방송에 올라간 경매가 바뀜 (`/topic/live-streams/{id}/auctions`). 받으면 방송의 경매 목록을 다시 불러온다. */
export type LiveAuctionChangedMessage = {
  type: 'CHANGED';
  auctionId: number;
};

/**
 * 경매 한 건의 변경 (`/topic/auctions/{id}`). 손님도 받는다.
 * - 입찰이 반영됨: bidderNickname(가린 닉네임)·amount·bidCount·endAt·extended (마감 30초 전 안쪽 입찰이면 endAt이 입찰 시각 + 30초로 늘어남)
 * - 경매가 끝남: result(ENDED 낙찰, FAILED 유찰)·finalPrice
 * 손님도 받으므로 회원 번호는 오지 않는다. 내가 최고 입찰자인지는 GET /auctions/{id}/bids/me (useMyBid)로 확인한다.
 */
export type AuctionRealtimeMessage =
  | { auctionId: number; bidderNickname: string; amount: number; bidCount: number; endAt: string; extended: boolean }
  | { auctionId: number; result: 'ENDED' | 'FAILED'; finalPrice: number | null; productName: string };

/**
 * AI가 확인이 필요하다고 본 채팅·선물 한마디 (`/user/queue/moderation`). 방송 판매자에게만 온다.
 * autoHidden이면 방송 설정(자동 가림)에 따라 이미 시청자 화면에서 가려졌다. 판매자는 되돌릴 수 있다.
 */
export type ModerationAlert = {
  liveStreamId: number;
  /** 문제의 채팅 messageId */
  messageId: string;
  /** CHAT(채팅) 또는 DONATION(선물 한마디) */
  messageType: 'CHAT' | 'DONATION';
  senderId: number;
  nickname: string;
  content: string;
  category: 'EXTERNAL_TRADE' | 'SCAM' | 'HARASSMENT' | 'HATE' | 'SEXUAL' | 'SPAM' | 'PERSONAL_INFO';
  /** 화면에 보여줄 분류 이름 */
  categoryLabel: string;
  /** 판단 이유 */
  reason: string;
  autoHidden: boolean;
  detectedAt: string;
};

/**
 * 송출이 멈춘 내 방송 안내 (`/user/queue/live-stream`). 방송 판매자에게만 온다.
 * 방송 중인데 송출이 25분 없으면 IDLE_WARNING(endsAt: 자동 종료 시각), 다시 송출하면 IDLE_RESUMED, 30분이면 방송이 끝나고 IDLE_ENDED
 */
export type LiveStreamIdleMessage = {
  type: 'IDLE_WARNING' | 'IDLE_RESUMED' | 'IDLE_ENDED';
  liveStreamId: number;
  endsAt: string | null;
};

/**
 * 1:1 채팅 이벤트 (`/user/queue/dm`). 내가 보낸 메시지도 온다. (같은 계정의 다른 탭·기기)
 * MESSAGE: message가 새 메시지 · READ: readerId가 lastReadMessageId까지 읽음 (보낸 사람 화면의 "1"을 지운다)
 */
/** 알림 (`/user/queue/notifications`). REST 알림함의 항목과 같은 형식. 구독한 판매자가 방송을 시작함 등 */
export type NotificationMessage = components['schemas']['NotificationResponse'];

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
  liveAuctions: (liveStreamId: number) => `/topic/live-streams/${liveStreamId}/auctions`,
  auction: (auctionId: number) => `/topic/auctions/${auctionId}`,
  errors: '/user/queue/errors',
  moderation: '/user/queue/moderation',
  liveStreamIdle: '/user/queue/live-stream',
  dm: '/user/queue/dm',
  notifications: '/user/queue/notifications',
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
