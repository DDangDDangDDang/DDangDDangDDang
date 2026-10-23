import { LocalAudioTrack, LocalVideoTrack } from 'livekit-client';
import { useCallback, useEffect, useRef, useState } from 'react';
import type { MediaKind } from '@/lib/media';
import type { LocalDevice } from './use-local-media';

type Playback = {
  video: HTMLVideoElement;
  url: string;
  audioContext: AudioContext;
  camera: LocalVideoTrack;
  microphone: LocalAudioTrack;
};

type State = {
  /** 고른 영상 파일 이름. 고르기 전이면 undefined */
  fileName?: string;
  camera?: LocalVideoTrack;
  microphone?: LocalAudioTrack;
  cameraOn: boolean;
  microphoneOn: boolean;
  loading: boolean;
  error?: string;
};

const INITIAL: State = { cameraOn: true, microphoneOn: true, loading: false };

/** 브라우저가 재생할 수 있는 영상 파일 (mp4·webm 등) */
export const RECORDED_VIDEO_ACCEPT = 'video/mp4,video/webm,video/*';

/** <video>의 화면을 스트림으로 받는 기능. (Chrome·Edge는 captureStream, Firefox는 mozCaptureStream) */
type CapturableVideo = HTMLVideoElement & {
  captureStream?: () => MediaStream;
  mozCaptureStream?: () => MediaStream;
};

const noDevice: (kind: MediaKind, deviceId: string) => Promise<void> = async () => undefined;

function stop(playback: Playback | undefined) {
  if (!playback) return;
  playback.video.pause();
  playback.video.removeAttribute('src');
  playback.video.load();
  URL.revokeObjectURL(playback.url);
  playback.camera.stop();
  playback.microphone.stop();
  void playback.audioContext.close();
}

/**
 * 미리 녹화한 영상 파일을 카메라·마이크 대신 송출한다.
 * - 판매자 브라우저에서 파일을 반복 재생(loop)하고, 그 화면과 소리를 LiveKit 트랙으로 만든다.
 *   서버에 파일을 올리지 않으므로 송출은 카메라처럼 이 탭이 열려 있는 동안만 이어진다.
 * - 소리는 Web Audio로 빼내 송출에만 쓰고 판매자 스피커로는 내보내지 않는다. (송출 화면을 보며 소리가 겹치지 않게)
 * - 카메라와 같은 모양(camera·microphone·toggle)으로 돌려줘 송출(usePublisher)과 화면은 소스를 몰라도 된다.
 * enabled가 false가 되면(방송이 끝나고 스튜디오를 떠남) 재생을 멈추고 파일을 잊는다.
 */
export function useRecordedVideo(enabled: boolean) {
  const [state, setState] = useState<State>(INITIAL);
  const playbackRef = useRef<Playback | undefined>(undefined);
  // 파일을 연달아 고르면 마지막에 고른 것만 쓴다.
  const requestRef = useRef(0);

  const clear = useCallback(() => {
    requestRef.current += 1;
    stop(playbackRef.current);
    playbackRef.current = undefined;
    setState(INITIAL);
  }, []);

  useEffect(() => {
    if (!enabled) clear();
  }, [enabled, clear]);
  useEffect(() => () => stop(playbackRef.current), []);

  const select = useCallback(async (file: File) => {
    const request = ++requestRef.current;
    setState((prev) => ({ ...prev, loading: true, error: undefined }));
    const url = URL.createObjectURL(file);
    const video = document.createElement('video') as CapturableVideo;
    video.loop = true;
    video.playsInline = true;
    video.src = url;
    let audioContext: AudioContext | undefined;
    try {
      audioContext = new AudioContext();
      const destination = audioContext.createMediaStreamDestination();
      audioContext.createMediaElementSource(video).connect(destination);
      await video.play();
      const capture = video.captureStream?.() ?? video.mozCaptureStream?.();
      const videoTrack = capture?.getVideoTracks()[0];
      const audioTrack = destination.stream.getAudioTracks()[0];
      if (!videoTrack || !audioTrack) throw new Error('no track');
      const playback: Playback = {
        video,
        url,
        audioContext,
        camera: new LocalVideoTrack(videoTrack, undefined, true),
        microphone: new LocalAudioTrack(audioTrack, undefined, true, audioContext),
      };
      if (request !== requestRef.current) {
        stop(playback);
        return;
      }
      stop(playbackRef.current);
      playbackRef.current = playback;
      setState({
        fileName: file.name,
        camera: playback.camera,
        microphone: playback.microphone,
        cameraOn: true,
        microphoneOn: true,
        loading: false,
      });
    } catch {
      video.pause();
      URL.revokeObjectURL(url);
      void audioContext?.close();
      if (request !== requestRef.current) return;
      setState((prev) => ({
        ...prev,
        loading: false,
        error: '이 영상을 재생하지 못했어요. mp4·webm 파일인지 확인하고, Chrome·Edge에서 다시 골라 주세요.',
      }));
    }
  }, []);

  /** 화면·소리 켜기/끄기. 끄면 트랙은 그대로 두고 음소거해 영상은 계속 반복 재생된다. */
  const toggle = useCallback(async (kind: MediaKind) => {
    const track = kind === 'camera' ? playbackRef.current?.camera : playbackRef.current?.microphone;
    if (!track) return;
    if (track.isMuted) await track.unmute();
    else await track.mute();
    setState((prev) => ({
      ...prev,
      [kind === 'camera' ? 'cameraOn' : 'microphoneOn']: !track.isMuted,
    }));
  }, []);

  const device = <K extends MediaKind>(track: LocalDevice<K>['track'], on: boolean): LocalDevice<K> => ({
    track,
    enabled: on,
    starting: state.loading,
    // 파일 오류는 장치 오류와 따로 보여 준다. (error)
    devices: [],
  });

  return {
    fileName: state.fileName,
    loading: state.loading,
    error: state.error,
    camera: device<'camera'>(state.camera, state.cameraOn),
    microphone: device<'microphone'>(state.microphone, state.microphoneOn),
    toggle,
    // 녹화 영상에는 고를 장치가 없다.
    selectDevice: noDevice,
    select,
    clear,
  };
}
