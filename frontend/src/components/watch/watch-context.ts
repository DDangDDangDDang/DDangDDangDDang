import { createContext, useContext, useEffect, useRef } from 'react';
import type { useLiveVideo } from '@/pages/live/use-live-video';

/** 지금 보고 있는 방송 (시청 화면을 떠나도 작은 플레이어로 이어 보는 방송) */
export type WatchSession = {
  liveStreamId: number;
  title?: string;
  live: boolean;
  /** 방송의 판매자. 로그인한 사용자의 방송이면 화면을 떠날 때 끊고 작은 플레이어를 띄우지 않는다. */
  sellerId?: number;
};

/**
 * 시청자 방송 시청 상태. 앱 전체에 하나만 두어(WatchProvider) 시청 화면을 떠나도 방송 연결(LiveKit)과 소리가 이어진다.
 * 시청 화면과 작은 플레이어(MiniPlayer)가 같이 쓴다.
 */
export type Watch = {
  session?: WatchSession;
  /** 이 방송의 시청 화면이 열려 있는지 (열려 있으면 작은 플레이어를 숨긴다) */
  pageOpen: boolean;
  /** 로그인한 사용자가 이 방송의 판매자인지. (내 방송은 작은 플레이어로 이어 보지 않는다) */
  mine: boolean;
  video: ReturnType<typeof useLiveVideo>;
  /** 방송 소리를 꺼 두었는지. 브라우저는 사용자가 누르기 전에는 소리를 막으므로 음소거로 시작한다. */
  muted: boolean;
  toggleSound: () => void;
  /** 작은 플레이어로 보던 중 방송이 끝났다. (잠깐 안내한 뒤 닫는다) */
  ended: boolean;
  open: (session: WatchSession, onRoomClosed: () => void) => void;
  update: (session: WatchSession) => void;
  leave: (liveStreamId: number) => void;
  /** 작은 플레이어를 닫고 방송 연결을 끊는다. */
  close: () => void;
};

export const WatchContext = createContext<Watch | null>(null);

export function useWatch(): Watch {
  const watch = useContext(WatchContext);
  if (!watch) throw new Error('useWatch는 WatchProvider 안에서만 쓸 수 있습니다.');
  return watch;
}

/**
 * 시청 화면이 이 방송을 보기 시작한다. 화면을 떠나도 방송 중이면 작은 플레이어에서 이어진다.
 * 같은 방송으로 돌아오면 다시 접속하지 않고 그대로 이어 본다.
 */
export function useWatchStream(session: WatchSession, onRoomClosed: () => void) {
  const watch = useWatch();
  const { open, update, leave } = watch;
  const onRoomClosedRef = useRef(onRoomClosed);
  useEffect(() => {
    onRoomClosedRef.current = onRoomClosed;
  });
  const { liveStreamId, title, live, sellerId } = session;

  useEffect(() => {
    open({ liveStreamId, live: false }, () => onRoomClosedRef.current());
    return () => leave(liveStreamId);
  }, [liveStreamId, open, leave]);

  useEffect(() => {
    update({ liveStreamId, title, live, sellerId });
  }, [liveStreamId, title, live, sellerId, update]);

  return watch;
}
