import { useCallback, useEffect, useRef, useState } from 'react';
import {
  connectRealtime,
  REALTIME,
  type ChatMessage,
  type LiveStreamUpdatedMessage,
  type RealtimeConnection,
  type RealtimeError,
  type RealtimeStatus,
} from '@/api';

type Handlers = {
  onChat: (message: ChatMessage) => void;
  onUpdated: (message: LiveStreamUpdatedMessage) => void;
  onError: (error: RealtimeError) => void;
};

/**
 * 방송 하나의 실시간 메시지: 채팅, 방송 정보 변경, 내 채팅 전송 실패. (docs/api/README.md "실시간 메시지")
 * 화면을 떠나면 연결을 닫는다.
 */
export function useLiveRealtime(liveStreamId: number, handlers: Handlers) {
  const [status, setStatus] = useState<RealtimeStatus>('connecting');
  const connectionRef = useRef<RealtimeConnection | null>(null);
  const handlersRef = useRef(handlers);
  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    const connection = connectRealtime(setStatus);
    connectionRef.current = connection;
    connection.subscribe<ChatMessage>(REALTIME.liveChat(liveStreamId), (message) => handlersRef.current.onChat(message));
    connection.subscribe<LiveStreamUpdatedMessage>(REALTIME.liveStream(liveStreamId), (message) => {
      if (message.type === 'UPDATED') handlersRef.current.onUpdated(message);
    });
    connection.subscribe<RealtimeError>(REALTIME.errors, (error) => handlersRef.current.onError(error));
    return () => {
      connection.close();
      connectionRef.current = null;
    };
  }, [liveStreamId]);

  /** 채팅을 보낸다. 연결돼 있지 않으면 false */
  const sendChat = useCallback(
    (content: string) => connectionRef.current?.publish(REALTIME.sendLiveChat(liveStreamId), { content }) ?? false,
    [liveStreamId],
  );

  return { status, sendChat };
}
