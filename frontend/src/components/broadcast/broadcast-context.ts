import { createContext, useContext, useEffect } from 'react';
import type { ChatMessage, ModerationAlert, RealtimeStatus, Schema } from '@/api';
import type { useLocalMedia } from './use-local-media';
import type { usePublisher } from './use-publisher';

/** 내 종료되지 않은 방송 (GET /live-streams/me/active). 판매자당 하나만 둘 수 있다. (LIVESTREAM_010) */
export const ACTIVE_STREAM_KEY = ['studio', 'active'] as const;

/** 방송 시작 요청 (POST /start). 요청 중에 스튜디오를 떠나도 송출 담당을 내려놓지 않도록 이름을 붙인다. */
export const START_STREAM_KEY = ['studio', 'start'] as const;

export type ActiveLiveStream = Schema<'LiveStreamResponse'> & { id: number };

/**
 * 판매자 송출 상태. 앱 전체에 하나만 두어(BroadcastProvider) 방송 중에 다른 화면으로 가도 송출·채팅이 이어진다.
 * 스튜디오 화면과 작은 방송 창(MiniBroadcast)이 같이 쓴다.
 */
export type Broadcast = {
  /** 내 종료되지 않은 방송 (없으면 undefined) */
  liveStream?: ActiveLiveStream;
  /** 방송 중인지 (서버 상태 LIVE) */
  live: boolean;
  /** 이 탭이 송출을 맡고 있는지. 스튜디오를 연 탭만 송출한다. (새 탭으로 연 화면은 송출하지 않음) */
  publishing: boolean;
  media: ReturnType<typeof useLocalMedia>;
  publisher: ReturnType<typeof usePublisher>;
  chat: {
    messages: ChatMessage[];
    status: RealtimeStatus;
    error?: string;
    send: (content: string) => boolean;
  };
  alerts: ModerationAlert[];
  dismissAlert: (messageId: string) => void;
  /**
   * 송출이 오래 없어 곧 자동 종료됨: 자동 종료 시각. (다시 송출하면 undefined)
   * 방송이 그렇게 끝났으면 autoEnded에 그 방송 제목이 남는다. (새 방송을 만들면 지움)
   */
  idle: { endsAt?: string; autoEnded?: string };
  /** 스튜디오를 떠나 있는 동안 온 채팅·검토 알림 수 (스튜디오로 돌아오면 0) */
  unseen: { chats: number; alerts: number };
  /** 방송 정보를 다시 불러온다. (방송 시작·종료 뒤, 서버 안내를 받았을 때) */
  reload: () => void;
  /** 스튜디오 화면이 열려 있는지 알린다. 방송 전에는 스튜디오에 있을 때만 카메라를 켠다. */
  setStudioOpen: (open: boolean) => void;
};

export const BroadcastContext = createContext<Broadcast | null>(null);

/** 판매자 송출 상태. BroadcastProvider(AppLayout) 안에서만 쓴다. */
export function useBroadcast(): Broadcast {
  const broadcast = useContext(BroadcastContext);
  if (!broadcast) throw new Error('useBroadcast는 BroadcastProvider 안에서만 쓸 수 있습니다.');
  return broadcast;
}

/** 스튜디오 화면이 열려 있는 동안 송출 상태에 알린다. (방송 전 미리보기 카메라, 안 본 채팅 수) */
export function useStudioPresence() {
  const { setStudioOpen } = useBroadcast();
  useEffect(() => {
    setStudioOpen(true);
    return () => setStudioOpen(false);
  }, [setStudioOpen]);
}
