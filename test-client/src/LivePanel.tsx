import { useEffect, useRef, useState } from 'react';
import {
  ConnectionState,
  DisconnectReason,
  RemoteTrack,
  Room,
  RoomEvent,
  Track,
} from 'livekit-client';
import { errorText, LiveStream, LiveStreamToken, liveStreamApi } from './api';
import type { Log } from './App';

type Props = {
  log: Log;
  liveStreamId: number | null;
  onLiveStreamIdChange: (id: number | null) => void;
};

type Mode = 'publisher' | 'viewer';

export default function LivePanel({ log, liveStreamId, onLiveStreamIdChange }: Props) {
  const [title, setTitle] = useState('테스트 방송');
  const [idInput, setIdInput] = useState(liveStreamId ? String(liveStreamId) : '');
  const [stream, setStream] = useState<LiveStream | null>(null);
  const [mode, setMode] = useState<Mode | null>(null);
  const [connection, setConnection] = useState<ConnectionState>(ConnectionState.Disconnected);
  const [needsAudioClick, setNeedsAudioClick] = useState(false);
  const [busy, setBusy] = useState(false);

  const roomRef = useRef<Room | null>(null);
  const videoBoxRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setIdInput(liveStreamId ? String(liveStreamId) : '');
  }, [liveStreamId]);

  // 화면을 떠나면 LiveKit 연결을 정리한다.
  useEffect(() => () => void roomRef.current?.disconnect(), []);

  async function run(label: string, action: () => Promise<void>) {
    setBusy(true);
    try {
      await action();
    } catch (e) {
      log(`${label} 실패: ${errorText(e)}`, 'error');
    } finally {
      setBusy(false);
    }
  }

  function currentId(): number {
    const id = Number(idInput);
    if (!Number.isInteger(id) || id <= 0) throw new Error('방송 ID를 입력해 주세요.');
    onLiveStreamIdChange(id);
    return id;
  }

  const create = () =>
    run('방송 생성', async () => {
      const created = await liveStreamApi.create(title);
      setStream(created);
      onLiveStreamIdChange(created.id);
      log(`방송 생성: id=${created.id}, 상태=${created.status}`);
    });

  const refresh = () =>
    run('방송 조회', async () => {
      const found = await liveStreamApi.get(currentId());
      setStream(found);
      log(`방송 조회: id=${found.id}, 상태=${found.status}`);
    });

  const start = () =>
    run('방송 시작', async () => {
      const id = currentId();
      const token = await liveStreamApi.start(id);
      log(`방송 시작: room=${token.roomName}`);
      setStream(await liveStreamApi.get(id));
      await connect(token, 'publisher');
    });

  const join = () =>
    run('시청 입장', async () => {
      const token = await liveStreamApi.join(currentId());
      log(`시청 입장: room=${token.roomName}`);
      await connect(token, 'viewer');
    });

  const end = () =>
    run('방송 종료', async () => {
      const ended = await liveStreamApi.end(currentId());
      setStream(ended);
      log(`방송 종료: id=${ended.id}`);
    });

  async function connect(token: LiveStreamToken, nextMode: Mode) {
    await roomRef.current?.disconnect();
    clearVideos();

    const room = new Room({ adaptiveStream: true, dynacast: true });
    roomRef.current = room;
    setMode(nextMode);

    room
      .on(RoomEvent.ConnectionStateChanged, (state) => setConnection(state))
      .on(RoomEvent.TrackSubscribed, (track: RemoteTrack, _pub, participant) => {
        attach(track.attach(), `${participant.name || participant.identity} · ${track.kind}`);
      })
      .on(RoomEvent.TrackUnsubscribed, (track: RemoteTrack) => {
        track.detach().forEach(removeTile);
      })
      .on(RoomEvent.ParticipantConnected, (p) => log(`참가: ${p.name || p.identity}`))
      .on(RoomEvent.ParticipantDisconnected, (p) => log(`퇴장: ${p.name || p.identity}`))
      .on(RoomEvent.AudioPlaybackStatusChanged, () => setNeedsAudioClick(!room.canPlaybackAudio))
      .on(RoomEvent.Disconnected, (reason) => {
        // 방송 종료로 방이 삭제되면 ROOM_DELETED
        log(`LiveKit 연결 종료 (사유: ${reason === undefined ? '-' : DisconnectReason[reason]})`);
        clearVideos();
        setMode(null);
      });

    await room.connect(token.serverUrl, token.token);
    log(`LiveKit 연결: ${token.serverUrl}`);

    if (nextMode === 'publisher') {
      // 방송은 이미 서버에서 시작된 상태라, 장치 권한 실패는 방송 시작 실패와 구분해서 알린다.
      try {
        await room.localParticipant.enableCameraAndMicrophone();
        const camera = room.localParticipant.getTrackPublication(Track.Source.Camera)?.videoTrack;
        if (camera) attach(camera.attach(), '내 카메라 (송출 중)');
        log('카메라·마이크 송출 시작');
      } catch (e) {
        log(`카메라·마이크를 켜지 못했어요 (방송은 시작된 상태): ${errorText(e)}`, 'error');
      }
    }
  }

  async function disconnect() {
    await roomRef.current?.disconnect();
    roomRef.current = null;
  }

  function attach(el: HTMLMediaElement, label: string) {
    const box = videoBoxRef.current;
    if (!box) return;
    if (el instanceof HTMLAudioElement) {
      el.dataset.tile = 'audio';
      box.appendChild(el);
      return;
    }
    const tile = document.createElement('figure');
    tile.className = 'tile';
    // 소리는 별도 오디오 트랙으로 재생되므로 영상은 음소거해 자동 재생이 막히지 않게 한다.
    el.muted = true;
    if (el instanceof HTMLVideoElement) el.playsInline = true;
    tile.appendChild(el);
    const caption = document.createElement('figcaption');
    caption.textContent = label;
    tile.appendChild(caption);
    box.appendChild(tile);
  }

  function removeTile(el: HTMLMediaElement) {
    const tile = el.closest('figure.tile');
    (tile ?? el).remove();
  }

  function clearVideos() {
    if (videoBoxRef.current) videoBoxRef.current.innerHTML = '';
  }

  return (
    <section className="panel">
      <h2>라이브 스트리밍</h2>

      <div className="row">
        <label className="grow">
          방송 제목
          <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={100} />
        </label>
        <button type="button" onClick={create} disabled={busy}>방송 만들기</button>
      </div>

      <div className="row">
        <label>
          방송 ID
          <input value={idInput} onChange={(e) => setIdInput(e.target.value)} inputMode="numeric" className="short" />
        </label>
        <button type="button" onClick={refresh} disabled={busy}>조회</button>
        <button type="button" className="primary" onClick={start} disabled={busy}>방송 시작 (판매자)</button>
        <button type="button" onClick={join} disabled={busy}>시청 입장</button>
        <button type="button" onClick={end} disabled={busy}>방송 종료</button>
      </div>

      {stream && (
        <p className="meta">
          #{stream.id} · {stream.title} · 판매자 id {stream.sellerId} · 상태 <strong>{stream.status}</strong>
        </p>
      )}

      <div className="row">
        <span className="meta">
          LiveKit: <strong>{connection}</strong>
          {mode && ` · ${mode === 'publisher' ? '송출' : '시청'} 모드`}
        </span>
        {needsAudioClick && (
          <button type="button" onClick={() => roomRef.current?.startAudio()}>소리 켜기</button>
        )}
        {mode && <button type="button" onClick={disconnect}>LiveKit 연결 끊기</button>}
      </div>

      <div ref={videoBoxRef} className="videos" aria-live="polite" />
    </section>
  );
}
