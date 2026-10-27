import { createLocalAudioTrack, createLocalVideoTrack, Room, VideoPresets, type LocalAudioTrack, type LocalVideoTrack } from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';
import { mediaErrorMessage, type MediaKind } from '@/lib/media';

type TrackOf = { camera: LocalVideoTrack; microphone: LocalAudioTrack };

export type LocalDevice<K extends MediaKind> = {
  /** 켜진(또는 꺼 둔) 트랙. 아직 만들지 못했거나 실패하면 undefined */
  track?: TrackOf[K];
  /** 켜져 있는지 (끄면 트랙은 그대로 두고 음소거한다. 카메라는 끄면 장치도 멈춘다) */
  enabled: boolean;
  /** 장치를 여는 중 */
  starting: boolean;
  /** 장치를 열지 못한 이유 (화면 문구) */
  error?: string;
  devices: MediaDeviceInfo[];
  deviceId?: string;
};

type State = { [K in MediaKind]: LocalDevice<K> };

const INITIAL: State = {
  camera: { enabled: true, starting: true, devices: [] },
  microphone: { enabled: true, starting: true, devices: [] },
};

const DEVICE_KIND: Record<MediaKind, MediaDeviceKind> = { camera: 'videoinput', microphone: 'audioinput' };

function createTrack<K extends MediaKind>(kind: K, deviceId?: string): Promise<TrackOf[K]> {
  const track =
    kind === 'camera'
      ? createLocalVideoTrack({ deviceId, resolution: VideoPresets.h720.resolution })
      : createLocalAudioTrack({ deviceId, echoCancellation: true, noiseSuppression: true });
  return track as Promise<TrackOf[K]>;
}

/**
 * 판매자의 카메라·마이크. 방송 시작 전에도 미리보기와 장치 선택을 할 수 있게 방(LiveKit)과 따로 둔다.
 * 송출은 usePublisher가 이 트랙을 방에 올려서 한다. enabled가 false가 되면(방송이 끝나고 스튜디오를 떠남) 장치를 멈춘다.
 * 앱 전체에 하나만 둔다. (BroadcastProvider)
 */
export function useLocalMedia(enabled: boolean) {
  const [state, setState] = useState<State>(INITIAL);
  const tracksRef = useRef<{ camera?: LocalVideoTrack; microphone?: LocalAudioTrack }>({});
  const unmountedRef = useRef(false);

  const patch = useCallback(<K extends MediaKind>(kind: K, next: Partial<LocalDevice<K>>) => {
    if (unmountedRef.current) return;
    setState((prev) => ({ ...prev, [kind]: { ...prev[kind], ...next } }));
  }, []);

  const refreshDevices = useCallback(async () => {
    for (const kind of ['camera', 'microphone'] as const) {
      const devices = await Room.getLocalDevices(DEVICE_KIND[kind], false).catch(() => []);
      patch(kind, { devices });
    }
  }, [patch]);

  const start = useCallback(
    async (kind: MediaKind, deviceId?: string) => {
      patch(kind, { starting: true, error: undefined });
      try {
        const track = await createTrack(kind, deviceId);
        if (unmountedRef.current) {
          track.stop();
          return;
        }
        tracksRef.current[kind]?.stop();
        tracksRef.current = { ...tracksRef.current, [kind]: track };
        patch(kind, { track, enabled: true, starting: false, deviceId: (await track.getDeviceId()) ?? deviceId });
        // 권한을 받은 뒤에야 장치 이름이 보이므로 목록을 다시 읽는다.
        void refreshDevices();
      } catch (error) {
        patch(kind, { starting: false, error: mediaErrorMessage(kind, error) });
      }
    },
    [patch, refreshDevices],
  );

  useEffect(() => {
    if (!enabled) {
      setState(INITIAL);
      return;
    }
    unmountedRef.current = false;
    void start('camera');
    void start('microphone');
    const onDeviceChange = () => void refreshDevices();
    navigator.mediaDevices?.addEventListener('devicechange', onDeviceChange);
    return () => {
      unmountedRef.current = true;
      navigator.mediaDevices?.removeEventListener('devicechange', onDeviceChange);
      tracksRef.current.camera?.stop();
      tracksRef.current.microphone?.stop();
      tracksRef.current = {};
    };
  }, [enabled, start, refreshDevices]);

  /** 켜기·끄기. 장치를 열지 못했던 경우에는 다시 열어 본다. */
  const toggle = useCallback(
    async (kind: MediaKind) => {
      const track = tracksRef.current[kind];
      if (!track) {
        await start(kind, state[kind].deviceId);
        return;
      }
      try {
        if (track.isMuted) await track.unmute();
        else await track.mute();
        patch(kind, { enabled: !track.isMuted, error: undefined });
      } catch (error) {
        patch(kind, { error: mediaErrorMessage(kind, error) });
      }
    },
    [patch, start, state],
  );

  /** 장치를 바꾼다. 송출 중이면 송출 중인 트랙도 그대로 바뀐다. */
  const selectDevice = useCallback(
    async (kind: MediaKind, deviceId: string) => {
      const track = tracksRef.current[kind];
      if (!track) {
        await start(kind, deviceId);
        return;
      }
      patch(kind, { deviceId, error: undefined });
      try {
        await track.setDeviceId(deviceId);
      } catch (error) {
        patch(kind, { error: mediaErrorMessage(kind, error) });
      }
    },
    [patch, start],
  );

  return { ...state, toggle, selectDevice };
}
