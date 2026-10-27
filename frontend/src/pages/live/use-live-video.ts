import { DisconnectReason, Room, RoomEvent, Track, type RemoteTrack } from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { api, call, isErrorCode } from '@/api';
import { summarizeParticipants } from '@/lib/live-room';

/**
 * idle: 방송 중이 아니라 접속하지 않음 · joining: 입장 토큰을 받아 접속 중 · connected: 접속됨
 * reconnecting: LiveKit이 스스로 다시 연결하는 중 · disconnected: 연결이 끊김(다시 시도 필요) · error: 입장 실패
 */
export type VideoPhase = 'idle' | 'joining' | 'connected' | 'reconnecting' | 'disconnected' | 'error';

export type LiveVideoState = {
  phase: VideoPhase;
  error?: unknown;
  videoTrack?: RemoteTrack;
  audioTracks: RemoteTrack[];
  sellerName?: string;
  /** 나를 포함한 시청자 수. 접속 전에는 undefined */
  viewerCount?: number;
};

const INITIAL: LiveVideoState = { phase: 'idle', audioTracks: [] };

/**
 * 방송 영상(LiveKit)에 시청자로 접속한다. enabled가 false(방송 중이 아님)면 접속하지 않고, 접속 중이었으면 끊는다.
 * 방송이 끝나 방이 사라지면 onRoomClosed를 부른다. (화면이 방송 정보를 다시 불러오게)
 */
export function useLiveVideo(liveStreamId: number, enabled: boolean, onRoomClosed: () => void) {
  const [state, setState] = useState<LiveVideoState>(INITIAL);
  const [attempt, setAttempt] = useState(0);
  const roomRef = useRef<Room | null>(null);
  const onRoomClosedRef = useRef(onRoomClosed);
  useEffect(() => {
    onRoomClosedRef.current = onRoomClosed;
  });

  useEffect(() => {
    if (!enabled) {
      setState(INITIAL);
      return;
    }

    let cancelled = false;
    const room = new Room({ adaptiveStream: true });
    roomRef.current = room;

    // 참가자·트랙이 바뀔 때마다 방 상태에서 다시 계산한다.
    const sync = (phase?: VideoPhase) => {
      if (cancelled) return;
      const participants = [...room.remoteParticipants.values()];
      let videoTrack: RemoteTrack | undefined;
      const audioTracks: RemoteTrack[] = [];
      for (const participant of participants) {
        for (const publication of participant.trackPublications.values()) {
          const track = publication.track;
          if (!track) continue;
          if (track.kind === Track.Kind.Video && !videoTrack) videoTrack = track;
          if (track.kind === Track.Kind.Audio) audioTracks.push(track);
        }
      }
      setState((prev) => ({
        ...prev,
        phase: phase ?? prev.phase,
        error: undefined,
        videoTrack,
        audioTracks,
        ...summarizeParticipants(participants),
      }));
    };

    room
      .on(RoomEvent.TrackSubscribed, () => sync())
      .on(RoomEvent.TrackUnsubscribed, () => sync())
      .on(RoomEvent.ParticipantConnected, () => sync())
      .on(RoomEvent.ParticipantDisconnected, () => sync())
      .on(RoomEvent.ParticipantNameChanged, () => sync())
      .on(RoomEvent.Reconnecting, () => sync('reconnecting'))
      .on(RoomEvent.Reconnected, () => sync('connected'))
      .on(RoomEvent.Disconnected, (reason) => {
        if (cancelled) return;
        setState({ phase: 'disconnected', audioTracks: [] });
        if (reason === DisconnectReason.ROOM_DELETED) onRoomClosedRef.current();
      });

    setState({ phase: 'joining', audioTracks: [] });
    void (async () => {
      try {
        const token = await call(api.POST('/api/v1/live-streams/{liveStreamId}/join', { params: { path: { liveStreamId } } }));
        if (cancelled) return;
        if (!token?.serverUrl || !token.token) throw new Error('방송 접속 정보가 없습니다.');
        await room.connect(token.serverUrl, token.token);
        sync('connected');
      } catch (error) {
        if (cancelled) return;
        setState({ phase: 'error', error, audioTracks: [] });
        // 그 사이 방송이 끝났으면 화면이 방송 정보를 다시 불러와 종료 화면을 보여준다.
        if (isErrorCode(error, 'LIVESTREAM_004')) onRoomClosedRef.current();
      }
    })();

    return () => {
      cancelled = true;
      roomRef.current = null;
      void room.disconnect();
    };
  }, [liveStreamId, enabled, attempt]);

  /** 끊긴 뒤·실패한 뒤 다시 접속한다. */
  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  /** 소리 재생을 허용한다. 브라우저 자동 재생 정책 때문에 사용자가 누른 뒤에 불러야 한다. */
  const startAudio = useCallback(() => roomRef.current?.startAudio(), []);

  return { ...state, retry, startAudio };
}
