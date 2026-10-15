import { useEffect, useRef, useState } from 'react';
import {
  ConnectionState,
  DisconnectReason,
  RemoteTrack,
  Room,
  RoomEvent,
  Track,
} from 'livekit-client';
import { errorText, LiveStream, LiveStreamToken, LiveStreamUpdate, liveStreamApi } from './api';
import type { Log } from './App';

type Props = {
  log: Log;
  userId: number;
  liveStreamId: number | null;
  onLiveStreamIdChange: (id: number | null) => void;
  // 채팅 연결로 받은 방송 정보 변경 알림 (다른 탭·다른 사용자의 수정도 여기로 온다)
  streamUpdate: LiveStream | null;
};

type Mode = 'publisher' | 'viewer';

export default function LivePanel({ log, userId, liveStreamId, onLiveStreamIdChange, streamUpdate }: Props) {
  const [title, setTitle] = useState('테스트 방송');
  const [description, setDescription] = useState('');
  // 방송 정보 수정 폼. 지금 방송 정보로 채워 두고, 바뀐 값만 보낸다.
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
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

  // 실시간으로 받은 방송 정보가 지금 보고 있는 방송이면 화면에 반영한다.
  useEffect(() => {
    if (streamUpdate && streamUpdate.id === liveStreamId) setStream(streamUpdate);
  }, [streamUpdate, liveStreamId]);

  // 방송 정보가 바뀌면(조회, 수정, 실시간 알림) 수정 폼도 그 값으로 다시 채운다.
  useEffect(() => {
    setEditTitle(stream?.title ?? '');
    setEditDescription(stream?.description ?? '');
  }, [stream?.id, stream?.title, stream?.description]);

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
      const created = await liveStreamApi.create(title, description);
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
      await connectPublisher(token);
    });

  // 방송이 LIVE가 된 뒤에는 방송 시작을 다시 할 수 없고(ALREADY_LIVE), 시청 입장 토큰으로는 송출할 수 없다.
  // 그래서 송출 토큰만 새로 받아 다시 접속한다. 새로고침이나 토큰 만료 뒤에도 같은 방법으로 복구된다.
  const reconnectPublisher = () =>
    run('송출 다시 연결', async () => {
      const token = await liveStreamApi.publisherToken(currentId());
      log(`송출 토큰 재발급: room=${token.roomName}`);
      await connectPublisher(token);
    });

  async function connectPublisher(token: LiveStreamToken) {
    try {
      await connect(token, 'publisher');
    } catch (e) {
      // 서버에서는 이미 방송이 LIVE이므로 방송 시작 실패가 아니라 송출 연결 실패로 알린다.
      throw new Error(`방송은 시작됐지만 LiveKit 송출 연결에 실패했어요. "송출 다시 연결"로 재시도해 주세요. (${errorText(e)})`);
    }
  }

  const join = () =>
    run('시청 입장', async () => {
      const token = await liveStreamApi.join(currentId());
      log(`시청 입장: room=${token.roomName}`);
      await connect(token, 'viewer');
    });

  // 바뀐 값만 보낸다. 설명을 모두 지우면 ''를 보내 서버에서 설명을 삭제한다.
  const update = () =>
    run('방송 정보 수정', async () => {
      if (!stream) return;
      const changes: LiveStreamUpdate = {};
      if (editTitle !== stream.title) changes.title = editTitle;
      if (editDescription !== (stream.description ?? '')) changes.description = editDescription;
      if (Object.keys(changes).length === 0) {
        log('바뀐 내용이 없어요.');
        return;
      }
      const updated = await liveStreamApi.update(stream.id, changes);
      setStream(updated);
      log(`방송 정보 수정: 제목="${updated.title}", 설명="${updated.description ?? ''}"`);
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
    // 다시 접속하면 room이 바뀌는데, 이전 room의 이벤트가 늦게 올 수 있다. 지금 room의 이벤트만 화면에 반영한다.
    const isCurrent = () => roomRef.current === room;

    room
      .on(RoomEvent.ConnectionStateChanged, (state) => {
        if (isCurrent()) setConnection(state);
      })
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
        if (!isCurrent()) return;
        // 방송 종료로 방이 삭제되면 ROOM_DELETED
        log(`LiveKit 연결 종료 (사유: ${reason === undefined ? '-' : DisconnectReason[reason]})`);
        clearVideos();
        setMode(null);
      });

    try {
      await room.connect(token.serverUrl, token.token);
    } catch (e) {
      // 접속 전에 실패하면 Disconnected 이벤트가 오지 않을 수 있어 여기서 정리한다.
      if (isCurrent()) {
        roomRef.current = null;
        setMode(null);
        setConnection(ConnectionState.Disconnected);
      }
      throw e;
    }
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
        <label className="grow">
          방송 설명 (선택)
          <input value={description} onChange={(e) => setDescription(e.target.value)} maxLength={1000} />
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
        <div className="stream-info">
          <p className="meta">
            #{stream.id} · <strong className="stream-title">{stream.title}</strong> · 판매자 id {stream.sellerId} · 상태{' '}
            <strong>{stream.status}</strong>
          </p>
          <p className="meta">{stream.description || '설명 없음'}</p>
        </div>
      )}

      {stream && stream.sellerId === userId && stream.status !== 'ENDED' && (
        <fieldset className="edit-box">
          <legend>방송 정보 수정 (판매자)</legend>
          <div className="row">
            <label className="grow">
              제목
              <input value={editTitle} onChange={(e) => setEditTitle(e.target.value)} maxLength={100} />
            </label>
          </div>
          <label>
            설명 (모두 지우고 저장하면 설명 삭제)
            <textarea
              value={editDescription}
              onChange={(e) => setEditDescription(e.target.value)}
              maxLength={1000}
              rows={3}
            />
          </label>
          <div className="row between">
            <span className="meta">
              {editDescription.length}/1000 · 방송 중에 바꾸면 채팅에 연결된 시청자 화면에도 바로 반영돼요
            </span>
            <button type="button" className="primary" onClick={update} disabled={busy}>정보 저장</button>
          </div>
        </fieldset>
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
        {stream?.status === 'LIVE' && stream.sellerId === userId && mode !== 'publisher' && (
          <button type="button" className="primary" onClick={reconnectPublisher} disabled={busy}>
            송출 다시 연결 (방송 #{stream.id})
          </button>
        )}
      </div>

      <div ref={videoBoxRef} className="videos" aria-live="polite" />
    </section>
  );
}
