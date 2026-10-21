import { DisconnectReason, Room, RoomEvent, Track, type LocalAudioTrack, type LocalVideoTrack } from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { api, call, isErrorCode, type Schema } from '@/api';
import { countViewers } from '@/lib/live-room';

/**
 * idle: 방송 중이 아니라 송출하지 않음 · connecting: 송출 토큰을 받아 접속 중 · connected: 송출 중
 * reconnecting: LiveKit이 스스로 다시 연결하는 중 · disconnected: 연결이 끊김 · error: 접속 실패
 * replaced: 다른 창(탭)에서 같은 방송을 송출해 이 창의 송출이 끊김
 */
export type PublishPhase = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'disconnected' | 'error' | 'replaced';

export type PublisherState = {
  phase: PublishPhase;
  error?: unknown;
  /** 판매자를 뺀 시청자 수. 접속 전에는 undefined */
  viewerCount?: number;
  /** 카메라·마이크를 방에 올리지 못한 오류 */
  publishError?: unknown;
};

type Token = Schema<'LiveStreamTokenResponse'>;
type LocalTracks = { camera?: LocalVideoTrack; microphone?: LocalAudioTrack };

const INITIAL: PublisherState = { phase: 'idle' };

/** 끊긴 뒤·접속 실패 뒤 스스로 다시 접속하는 횟수와 간격. 그 뒤로는 [다시 연결] 버튼으로 한다. */
const AUTO_RETRY_LIMIT = 3;
const AUTO_RETRY_DELAY_MS = 2000;

/**
 * 판매자 송출(LiveKit). live가 true(방송 중)면 방에 접속해 카메라·마이크 트랙을 올린다.
 * - 방송 시작(POST /start)으로 받은 토큰은 handOffToken으로 넘겨 그대로 쓴다.
 * - 새로고침·연결 끊김 뒤에는 POST /publisher-token으로 토큰을 다시 받아 접속한다.
 * - 방송이 끝나 방이 사라지면 onRoomClosed를 부른다. (화면이 방송 정보를 다시 불러오게)
 * 방을 나가도 트랙은 멈추지 않는다. (트랙은 useLocalMedia가 관리)
 */
export function usePublisher(liveStreamId: number, live: boolean, tracks: LocalTracks, onRoomClosed: () => void) {
  const [state, setState] = useState<PublisherState>(INITIAL);
  const [attempt, setAttempt] = useState(0);
  const [room, setRoom] = useState<Room | null>(null);
  const pendingTokenRef = useRef<Token | null>(null);
  const autoRetriesRef = useRef(0);
  const onRoomClosedRef = useRef(onRoomClosed);
  useEffect(() => {
    onRoomClosedRef.current = onRoomClosed;
  });

  useEffect(() => {
    if (!live) {
      setState(INITIAL);
      return;
    }

    let cancelled = false;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    const current = new Room({ dynacast: true, stopLocalTrackOnUnpublish: false });

    const scheduleAutoRetry = () => {
      if (autoRetriesRef.current >= AUTO_RETRY_LIMIT) return;
      autoRetriesRef.current += 1;
      retryTimer = setTimeout(() => setAttempt((n) => n + 1), AUTO_RETRY_DELAY_MS);
    };
    const syncViewers = () => {
      if (!cancelled) setState((prev) => ({ ...prev, viewerCount: countViewers(current.remoteParticipants.values()) }));
    };

    current
      .on(RoomEvent.ParticipantConnected, syncViewers)
      .on(RoomEvent.ParticipantDisconnected, syncViewers)
      .on(RoomEvent.Reconnecting, () => !cancelled && setState((prev) => ({ ...prev, phase: 'reconnecting' })))
      .on(RoomEvent.Reconnected, () => !cancelled && setState((prev) => ({ ...prev, phase: 'connected' })))
      .on(RoomEvent.Disconnected, (reason) => {
        if (cancelled) return;
        setRoom(null);
        if (reason === DisconnectReason.ROOM_DELETED) {
          setState({ phase: 'disconnected' });
          onRoomClosedRef.current();
        } else if (reason === DisconnectReason.DUPLICATE_IDENTITY) {
          // 다른 창이 송출을 가져갔다. 서로 다시 빼앗지 않도록 스스로 다시 접속하지 않는다.
          setState({ phase: 'replaced' });
        } else {
          setState({ phase: 'disconnected' });
          scheduleAutoRetry();
        }
      });

    setState({ phase: 'connecting' });
    void (async () => {
      try {
        let token = pendingTokenRef.current;
        pendingTokenRef.current = null;
        token ??= await call(api.POST('/api/v1/live-streams/{liveStreamId}/publisher-token', { params: { path: { liveStreamId } } }));
        if (cancelled) return;
        if (!token?.serverUrl || !token.token) throw new Error('송출 접속 정보가 없습니다.');
        await current.connect(token.serverUrl, token.token);
        if (cancelled) return;
        autoRetriesRef.current = 0;
        setRoom(current);
        setState({ phase: 'connected', viewerCount: countViewers(current.remoteParticipants.values()) });
      } catch (error) {
        if (cancelled) return;
        setState({ phase: 'error', error });
        if (isErrorCode(error, 'LIVESTREAM_004')) {
          // 그 사이 방송이 끝났다. 화면이 방송 정보를 다시 불러온다.
          onRoomClosedRef.current();
        } else if (!isErrorCode(error, 'LIVESTREAM_001') && !isErrorCode(error, 'LIVESTREAM_002')) {
          scheduleAutoRetry();
        }
      }
    })();

    return () => {
      cancelled = true;
      clearTimeout(retryTimer);
      setRoom(null);
      void current.disconnect(false);
    };
  }, [liveStreamId, live, attempt]);

  // 접속한 방에 지금 트랙을 올린다. 장치를 다시 열어 트랙이 바뀌면 이전 트랙을 내리고 새 트랙을 올린다.
  const { camera, microphone } = tracks;
  const publishingRef = useRef(new WeakSet<object>());
  useEffect(() => {
    if (!room) return;
    const participant = room.localParticipant;
    const wanted = [camera, microphone].filter((track) => track !== undefined);
    for (const publication of participant.trackPublications.values()) {
      const track = publication.track;
      if (track && !wanted.includes(track as LocalVideoTrack | LocalAudioTrack)) void participant.unpublishTrack(track, false);
    }
    for (const track of wanted) {
      const published = [...participant.trackPublications.values()].some((publication) => publication.track === track);
      if (published || publishingRef.current.has(track)) continue;
      publishingRef.current.add(track);
      const source = track.kind === Track.Kind.Video ? Track.Source.Camera : Track.Source.Microphone;
      participant
        .publishTrack(track, { source })
        .then(() => setState((prev) => ({ ...prev, publishError: undefined })))
        .catch((publishError: unknown) => setState((prev) => ({ ...prev, publishError })))
        .finally(() => publishingRef.current.delete(track));
    }
  }, [room, camera, microphone]);

  /** 끊긴 뒤·실패한 뒤 다시 접속한다. (/publisher-token으로 토큰을 다시 받는다) */
  const retry = useCallback(() => {
    autoRetriesRef.current = 0;
    setAttempt((n) => n + 1);
  }, []);

  /** 방송 시작 응답의 송출 토큰을 넘긴다. 방송 상태가 LIVE로 바뀌어 접속할 때 이 토큰을 먼저 쓴다. */
  const handOffToken = useCallback((token: Token | undefined) => {
    pendingTokenRef.current = token ?? null;
  }, []);

  return { ...state, retry, handOffToken };
}
