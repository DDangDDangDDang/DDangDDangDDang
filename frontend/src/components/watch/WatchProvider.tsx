import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLiveVideo } from '@/pages/live/use-live-video';
import { WatchContext, type Watch, type WatchSession } from './watch-context';

/** 작은 플레이어로 보던 방송이 끝나면 이만큼 안내한 뒤 닫는다. */
const ENDED_NOTICE_MS = 5_000;

/**
 * 시청자 방송 시청을 앱 전체에 하나만 둔다. (AppLayout)
 * - 시청 화면이 방송을 열면(useWatchStream) 여기서 LiveKit에 접속한다. 화면을 떠나도 방송 중이면 연결과 소리를 그대로 두고
 *   작은 플레이어(MiniPlayer)로 이어 보여 준다. 같은 방송으로 돌아오면 다시 접속하지 않는다.
 * - 다른 방송의 시청 화면을 열면 그 방송으로 바꾼다. (한 번에 하나만 본다)
 * - 작은 플레이어의 [닫기]나 방송 종료로 연결을 끊는다. 방송 중이 아닌(예정·종료) 방송은 화면을 떠나면 바로 끊는다.
 */
export function WatchProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<WatchSession>();
  const [pageStreamId, setPageStreamId] = useState<number>();
  const [ended, setEnded] = useState(false);
  const [muted, setMuted] = useState(true);
  // 방송이 끝나 방이 사라졌을 때: 시청 화면이 열려 있으면 화면이 방송 정보를 다시 불러오고, 작은 플레이어면 끝났다고 알린다.
  const pageStreamIdRef = useRef<number | undefined>(undefined);
  const onPageRoomClosedRef = useRef<(() => void) | undefined>(undefined);
  useEffect(() => {
    pageStreamIdRef.current = pageStreamId;
  }, [pageStreamId]);

  const onRoomClosed = useCallback(() => {
    if (pageStreamIdRef.current !== undefined) onPageRoomClosedRef.current?.();
    else setEnded(true);
  }, []);

  const liveStreamId = session?.liveStreamId ?? 0;
  const video = useLiveVideo(liveStreamId, !!session?.live && !ended, onRoomClosed);

  // 지금 보고 있는 방송. (open에서 다른 방송으로 바뀌는지 판단할 때 쓴다)
  const sessionIdRef = useRef<number | undefined>(undefined);
  useEffect(() => {
    sessionIdRef.current = session?.liveStreamId;
  }, [session?.liveStreamId]);

  const open = useCallback((next: WatchSession, onPageRoomClosed: () => void) => {
    onPageRoomClosedRef.current = onPageRoomClosed;
    setPageStreamId(next.liveStreamId);
    if (sessionIdRef.current !== next.liveStreamId) setEnded(false);
    // 작은 플레이어로 보던 방송으로 돌아오면 연결을 그대로 쓴다.
    setSession((prev) => (prev?.liveStreamId === next.liveStreamId ? prev : next));
  }, []);

  const update = useCallback((next: WatchSession) => {
    setSession((prev) => (prev?.liveStreamId === next.liveStreamId ? { ...prev, ...next } : prev));
  }, []);

  const leave = useCallback((id: number) => {
    setPageStreamId((current) => (current === id ? undefined : current));
    // 방송 중이 아니면 이어 볼 것이 없으므로 끊는다.
    setSession((prev) => (prev?.liveStreamId === id && !prev.live ? undefined : prev));
  }, []);

  const close = useCallback(() => {
    setSession(undefined);
    setEnded(false);
  }, []);

  useEffect(() => {
    if (!ended || pageStreamId !== undefined) return;
    const timer = setTimeout(close, ENDED_NOTICE_MS);
    return () => clearTimeout(timer);
  }, [ended, pageStreamId, close]);

  const { startAudio } = video;
  const toggleSound = useCallback(() => {
    // 소리를 켤 때는 사용자가 누른 순간에 재생을 허용한다.
    if (muted) void startAudio();
    setMuted(!muted);
  }, [muted, startAudio]);

  const value = useMemo<Watch>(
    () => ({
      session,
      pageOpen: session !== undefined && pageStreamId === session.liveStreamId,
      video,
      muted,
      toggleSound,
      ended,
      open,
      update,
      leave,
      close,
    }),
    [session, pageStreamId, video, muted, toggleSound, ended, open, update, leave, close],
  );

  return <WatchContext.Provider value={value}>{children}</WatchContext.Provider>;
}
