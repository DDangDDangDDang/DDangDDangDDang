import { useIsMutating, useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { ApiError, api, call, errorMessage, useSession, type ChatMessage, type ModerationAlert } from '@/api';
import { readBroadcastOwner, writeBroadcastOwner } from '@/lib/broadcast-owner';
import { appendChatMessage, prependModerationAlert } from '@/lib/live-room';
import { useLiveRealtime } from '@/lib/use-live-realtime';
import { ACTIVE_STREAM_KEY, BroadcastContext, START_STREAM_KEY, type ActiveLiveStream, type Broadcast } from './broadcast-context';
import { useLocalMedia } from './use-local-media';
import { usePublisher } from './use-publisher';

/**
 * 판매자 송출을 앱 전체에 하나만 둔다. (AppLayout)
 * - 방송 중이면 어느 화면에 있든 카메라·마이크·LiveKit 송출과 방송 채팅·검토 알림 연결을 유지한다.
 *   송출은 스튜디오를 연 탭만 맡는다. 스튜디오에서 새 탭으로 연 화면이 송출을 빼앗지 않도록. (lib/broadcast-owner)
 * - 방송 전에는 스튜디오 화면이 열려 있을 때만 카메라를 켠다. (미리보기)
 * - 새로고침·탭 닫기는 브라우저가 페이지를 없애 송출이 끊기므로, 방송 중에는 떠나기 전에 확인 창을 띄운다.
 *   새로고침한 뒤에는 방송 중인 방송을 찾아 /publisher-token으로 다시 접속한다.
 */
export function BroadcastProvider({ children }: { children: ReactNode }) {
  const session = useSession();
  const queryClient = useQueryClient();
  const active = useQuery({
    queryKey: ACTIVE_STREAM_KEY,
    queryFn: () => call(api.GET('/api/v1/live-streams/me/active')),
    enabled: session !== null,
  });
  const liveStream = session && active.data?.id && active.data.status !== 'ENDED' ? (active.data as ActiveLiveStream) : undefined;
  const liveStreamId = liveStream?.id;
  const live = liveStream?.status === 'LIVE';

  const [studioOpen, setStudioOpenState] = useState(false);
  const studioOpenRef = useRef(false);
  const setStudioOpen = useCallback((open: boolean) => {
    studioOpenRef.current = open;
    setStudioOpenState(open);
  }, []);

  // 이 탭이 송출을 맡았는지: 스튜디오를 열면 맡고, 방송이 없어지거나 방송 전에 스튜디오를 떠나면 내려놓는다. (새로고침해도 이 탭에 남음)
  // 방송 전에 떠난 탭이 맡은 채로 남으면, 다른 탭에서 방송을 시작할 때 이 탭도 송출해 서로 빼앗는다.
  const [ownerId, setOwnerId] = useState(readBroadcastOwner);
  if (studioOpen && liveStreamId !== undefined && ownerId !== liveStreamId) setOwnerId(liveStreamId);
  // [방송 시작]을 누르고 응답 전에 스튜디오를 떠났으면, 곧 방송 중이 되므로 내려놓지 않는다.
  const starting = useIsMutating({ mutationKey: START_STREAM_KEY }) > 0;
  if (active.isSuccess && ownerId !== undefined && (liveStreamId === undefined || (!live && !studioOpen && !starting))) setOwnerId(undefined);
  useEffect(() => writeBroadcastOwner(ownerId), [ownerId]);
  const publishing = live && ownerId === liveStreamId;

  const reload = useCallback(() => void queryClient.invalidateQueries({ queryKey: ACTIVE_STREAM_KEY }), [queryClient]);

  // 카메라·마이크와 송출 (LiveKit)
  const media = useLocalMedia(publishing || (liveStream !== undefined && studioOpen));
  const publisher = usePublisher(liveStreamId ?? 0, publishing, { camera: media.camera.track, microphone: media.microphone.track }, reload);

  // 방송 채팅·검토 알림 (STOMP). 방송이 바뀌면 처음부터 쌓는다.
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [chatError, setChatError] = useState<string>();
  const [alerts, setAlerts] = useState<ModerationAlert[]>([]);
  const [unseen, setUnseen] = useState({ chats: 0, alerts: 0 });
  // 송출이 멈춘 방송 자동 종료 안내. 알림을 받은 뒤 방송이 끝나면 자동 종료로 본다. (종료 안내가 연결이 닫힌 뒤 와도)
  const [idleEndsAt, setIdleEndsAt] = useState<string>();
  const [autoEnded, setAutoEnded] = useState<string>();
  const [prevStream, setPrevStream] = useState(liveStream);
  const [prevStreamId, setPrevStreamId] = useState(liveStreamId);
  if (prevStreamId !== liveStreamId) {
    if (liveStreamId === undefined && idleEndsAt !== undefined) setAutoEnded(prevStream?.title || '방송');
    if (liveStreamId !== undefined) setAutoEnded(undefined);
    setIdleEndsAt(undefined);
    setPrevStreamId(liveStreamId);
    setMessages([]);
    setAlerts([]);
    setChatError(undefined);
    setUnseen({ chats: 0, alerts: 0 });
  }
  if (prevStream !== liveStream && liveStream !== undefined) setPrevStream(liveStream);
  // 스튜디오로 돌아오면 안 본 수를 지운다.
  const [prevStudioOpen, setPrevStudioOpen] = useState(studioOpen);
  if (prevStudioOpen !== studioOpen) {
    setPrevStudioOpen(studioOpen);
    if (studioOpen) setUnseen({ chats: 0, alerts: 0 });
  }

  // 채팅·검토 알림은 송출을 맡은 탭과 스튜디오를 연 탭만 받는다. (다른 탭은 쓰지 않으므로 연결하지 않음)
  const realtimeStreamId = liveStreamId !== undefined && (ownerId === liveStreamId || studioOpen) ? liveStreamId : undefined;
  const realtime = useLiveRealtime(realtimeStreamId, {
    onChat: (message) => {
      setMessages((list) => appendChatMessage(list, message));
      if (message.type === 'CHAT' && !studioOpenRef.current) setUnseen((prev) => ({ ...prev, chats: prev.chats + 1 }));
      // 서버 안내(방송 시작·종료 등)가 오면 방송 상태가 바뀌었을 수 있으므로 다시 불러온다.
      if (message.type === 'SYSTEM') reload();
    },
    onUpdated: (message) => queryClient.setQueryData(ACTIVE_STREAM_KEY, message.liveStream),
    onError: (error) => setChatError(errorMessage(new ApiError(error.code ?? '', error.message ?? '', 0))),
    onModeration: (alert) => {
      setAlerts((list) => prependModerationAlert(list, alert));
      if (!studioOpenRef.current) setUnseen((prev) => ({ ...prev, alerts: prev.alerts + 1 }));
    },
    onIdle: (message) => {
      if (message.type === 'IDLE_WARNING') setIdleEndsAt(message.endsAt ?? undefined);
      if (message.type === 'IDLE_RESUMED') setIdleEndsAt(undefined);
      if (message.type === 'IDLE_ENDED') {
        setAutoEnded(liveStream?.title || '방송');
        setIdleEndsAt(undefined);
        reload();
      }
    },
  });

  // 방송 중 새로고침·탭 닫기·다른 사이트로 이동 전에 확인한다. (같은 앱 안의 화면 이동은 송출이 이어지므로 묻지 않는다)
  useEffect(() => {
    if (!publishing) return;
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      // 오래된 브라우저는 returnValue가 있어야 확인 창을 띄운다.
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [publishing]);

  const sendChat = realtime.sendChat;
  const value = useMemo<Broadcast>(
    () => ({
      liveStream,
      live,
      publishing,
      media,
      publisher,
      chat: {
        messages,
        status: realtime.status,
        error: chatError,
        send: (content) => {
          setChatError(undefined);
          return sendChat(content);
        },
      },
      alerts,
      dismissAlert: (messageId) => setAlerts((list) => list.filter((a) => a.messageId !== messageId)),
      idle: { endsAt: idleEndsAt, autoEnded },
      unseen,
      reload,
      setStudioOpen,
    }),
    [liveStream, live, publishing, media, publisher, messages, realtime.status, chatError, sendChat, alerts, idleEndsAt, autoEnded, unseen, reload, setStudioOpen],
  );

  return <BroadcastContext.Provider value={value}>{children}</BroadcastContext.Provider>;
}
