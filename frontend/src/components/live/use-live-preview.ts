import { Room, RoomEvent, Track, type RemoteTrack, type RemoteTrackPublication } from 'livekit-client';
import { useCallback, useContext, useEffect, useRef, useState } from 'react';
import { api, call } from '@/api';
import { WatchContext } from '@/components/watch/watch-context';

type PreviewTracks = { liveStreamId?: number; videoTrack?: RemoteTrack; audioTrack?: RemoteTrack };

/**
 * 방송 미리보기 연결 (홈 방송 카드·대표 라이브). liveStreamId가 있을 때만 접속하고, undefined가 되면 바로 끊는다.
 * - 미리보기 토큰(POST /preview)으로 숨은 참가자로 들어가므로 시청자 수에 잡히지 않는다.
 * - 자동 구독을 끄고 영상만 구독한다. 소리는 audio가 true일 때만 구독한다. (대표 라이브의 [소리 켜기])
 * - 실패해도 화면은 썸네일을 그대로 보여 주면 되므로 오류를 따로 알리지 않는다.
 */
export function useLivePreview(liveStreamId: number | undefined, audio = false) {
  const [tracks, setTracks] = useState<PreviewTracks>({});
  const roomRef = useRef<Room | null>(null);
  const audioRef = useRef(audio);

  useEffect(() => {
    if (liveStreamId === undefined) return;

    let cancelled = false;
    const room = new Room({ adaptiveStream: true });
    roomRef.current = room;

    const subscribe = (publication: RemoteTrackPublication) => {
      publication.setSubscribed(publication.kind === Track.Kind.Video || audioRef.current);
    };
    const sync = () => {
      if (cancelled) return;
      let videoTrack: RemoteTrack | undefined;
      let audioTrack: RemoteTrack | undefined;
      for (const participant of room.remoteParticipants.values()) {
        for (const publication of participant.trackPublications.values()) {
          const track = publication.track;
          if (track?.kind === Track.Kind.Video) videoTrack ??= track;
          if (track?.kind === Track.Kind.Audio) audioTrack ??= track;
        }
      }
      setTracks({ liveStreamId, videoTrack, audioTrack });
    };

    room
      .on(RoomEvent.TrackPublished, subscribe)
      .on(RoomEvent.TrackSubscribed, sync)
      .on(RoomEvent.TrackUnsubscribed, sync)
      .on(RoomEvent.ParticipantDisconnected, sync)
      .on(RoomEvent.Disconnected, sync);

    void (async () => {
      try {
        const token = await call(api.POST('/api/v1/live-streams/{liveStreamId}/preview', { params: { path: { liveStreamId } } }));
        if (cancelled || !token?.serverUrl || !token.token) return;
        await room.connect(token.serverUrl, token.token, { autoSubscribe: false });
        if (cancelled) return;
        // 접속 전에 이미 송출 중인 트랙
        for (const participant of room.remoteParticipants.values()) {
          for (const publication of participant.trackPublications.values()) subscribe(publication);
        }
      } catch {
        // 미리보기는 실패하면 썸네일을 그대로 둔다. (방송 종료, 요청 제한 등)
      }
    })();

    return () => {
      cancelled = true;
      roomRef.current = null;
      void room.disconnect();
    };
  }, [liveStreamId]);

  // 소리 켜기·끄기: 이미 접속해 있으면 소리 트랙만 구독하거나 끊는다.
  useEffect(() => {
    audioRef.current = audio;
    const room = roomRef.current;
    if (!room) return;
    for (const participant of room.remoteParticipants.values()) {
      for (const publication of participant.trackPublications.values()) {
        if (publication.kind === Track.Kind.Audio) publication.setSubscribed(audio);
      }
    }
  }, [audio]);

  /** 소리 재생을 허용한다. 브라우저 자동 재생 정책 때문에 사용자가 누른 순간에 부른다. */
  const startAudio = useCallback(() => roomRef.current?.startAudio(), []);

  // 다른 방송으로 바뀌었거나 끊은 뒤에는 이전 트랙을 보여 주지 않는다.
  const current = liveStreamId !== undefined && tracks.liveStreamId === liveStreamId;
  return {
    videoTrack: current ? tracks.videoTrack : undefined,
    audioTrack: current ? tracks.audioTrack : undefined,
    startAudio,
  };
}

/**
 * 방송 카드에 보여 줄 미리보기 영상. active일 때만 연결한다.
 * 작은 플레이어(WatchProvider)로 이미 보고 있는 방송이면 새로 접속하지 않고 그 연결의 영상을 같이 보여 준다. (shared)
 */
export function useStreamPreview(liveStreamId: number, active: boolean, audio = false) {
  const watch = useContext(WatchContext);
  const shared = watch?.session?.liveStreamId === liveStreamId && watch.session.live;
  const own = useLivePreview(active && !shared ? liveStreamId : undefined, audio);
  if (shared) return { videoTrack: active ? watch.video.videoTrack : undefined, audioTrack: undefined, shared: true, startAudio: own.startAudio };
  return { ...own, shared: false };
}
