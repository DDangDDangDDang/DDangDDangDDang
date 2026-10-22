import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import {
  connectRealtime,
  useSession,
  REALTIME,
  type ChatMessage,
  type LiveAuctionChangedMessage,
  type LiveStreamIdleMessage,
  type LiveStreamUpdatedMessage,
  type ModerationAlert,
  type RealtimeConnection,
  type RealtimeError,
  type RealtimeStatus,
} from '@/api';
import { liveStreamAuctionsKey } from './use-live-stream-auctions';

type Handlers = {
  onChat: (message: ChatMessage) => void;
  onUpdated: (message: LiveStreamUpdatedMessage) => void;
  onError: (error: RealtimeError) => void;
  /** 판매자 화면만 넘긴다. 넘기면 AI 검토 알림(/user/queue/moderation)도 구독한다. */
  onModeration?: (alert: ModerationAlert) => void;
  /** 판매자 화면만 넘긴다. 넘기면 송출이 멈춘 내 방송 안내(/user/queue/live-stream)도 구독한다. */
  onIdle?: (message: LiveStreamIdleMessage) => void;
};

/**
 * 방송 하나의 실시간 메시지: 채팅, 방송 정보 변경, 방송의 경매 변경(경매 목록을 다시 불러옴), 내 채팅 전송 실패, (판매자) AI 검토 알림. (docs/api/README.md "실시간 메시지")
 * 방송 시청 화면과 판매자 송출(BroadcastProvider)이 같이 쓴다. liveStreamId가 없거나 화면을 떠나면 연결을 닫는다.
 */
export function useLiveRealtime(liveStreamId: number | undefined, handlers: Handlers) {
  const [status, setStatus] = useState<RealtimeStatus>('connecting');
  const connectionRef = useRef<RealtimeConnection | null>(null);
  const handlersRef = useRef(handlers);
  useEffect(() => {
    handlersRef.current = handlers;
  });
  const withModeration = handlers.onModeration !== undefined;
  const withIdle = handlers.onIdle !== undefined;
  // 로그인하지 않았으면 손님으로 연결해 채팅을 보기만 한다. 로그인·로그아웃하면 다시 연결한다.
  const userId = useSession()?.user.userId;
  const guest = userId === undefined;
  const queryClient = useQueryClient();

  // 로그인 직후 입력 이벤트가 이전 손님 연결로 보내지지 않도록, 화면을 그린 즉시(useLayoutEffect) 연결을 바꾼다.
  useLayoutEffect(() => {
    if (liveStreamId === undefined) return;
    const connection = connectRealtime(setStatus, { guest });
    connectionRef.current = connection;
    connection.subscribe<ChatMessage>(REALTIME.liveChat(liveStreamId), (message) => handlersRef.current.onChat(message));
    connection.subscribe<LiveStreamUpdatedMessage>(REALTIME.liveStream(liveStreamId), (message) => {
      if (message.type === 'UPDATED') handlersRef.current.onUpdated(message);
    });
    connection.subscribe<LiveAuctionChangedMessage>(REALTIME.liveAuctions(liveStreamId), () => {
      void queryClient.invalidateQueries({ queryKey: liveStreamAuctionsKey(liveStreamId) });
    });
    // 개인 큐(전송 실패·검토 알림)는 로그인한 연결만 구독할 수 있다.
    if (!guest) connection.subscribe<RealtimeError>(REALTIME.errors, (error) => handlersRef.current.onError(error));
    if (withModeration && !guest) {
      connection.subscribe<ModerationAlert>(REALTIME.moderation, (alert) => {
        // 판매자 한 명에게 오는 알림이라 다른 방송(지난 방송 등)의 알림은 건너뛴다.
        if (alert.liveStreamId === liveStreamId) handlersRef.current.onModeration?.(alert);
      });
    }
    if (withIdle && !guest) {
      connection.subscribe<LiveStreamIdleMessage>(REALTIME.liveStreamIdle, (message) => {
        if (message.liveStreamId === liveStreamId) handlersRef.current.onIdle?.(message);
      });
    }
    return () => {
      connection.close();
      connectionRef.current = null;
    };
  }, [liveStreamId, withModeration, withIdle, guest, userId, queryClient]);

  /** 채팅을 보낸다. 연결돼 있지 않으면 false */
  const sendChat = useCallback(
    (content: string) => (liveStreamId === undefined ? false : (connectionRef.current?.publish(REALTIME.sendLiveChat(liveStreamId), { content }) ?? false)),
    [liveStreamId],
  );

  return { status, sendChat, guest };
}
