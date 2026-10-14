import { FormEvent, useEffect, useRef, useState } from 'react';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { getAccessToken, reissue } from './api';
import type { Log } from './App';

type Props = { log: Log; liveStreamId: number | null };

type ChatItem =
  | { kind: 'chat'; key: string; nickname: string; senderId: number; content: string; sentAt: string }
  | { kind: 'system'; key: string; content: string; sentAt: string }
  | { kind: 'error'; key: string; code: string; content: string };

type ServerMessage = {
  type: 'CHAT' | 'SYSTEM';
  liveStreamId: number;
  senderId: number | null;
  nickname: string | null;
  content: string;
  sentAt: string;
};

let seq = 0;
const nextKey = () => String(++seq);

function wsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/ws`;
}

export default function ChatPanel({ log, liveStreamId }: Props) {
  const [connected, setConnected] = useState(false);
  // 연결에 성공할 때마다 1씩 오른다. 재연결로 client가 바뀌어도 connected가 true로 유지될 수 있어서,
  // 이 값으로 새 client에 채팅 구독을 다시 건다.
  const [connectionGen, setConnectionGen] = useState(0);
  const [items, setItems] = useState<ChatItem[]>([]);
  const [input, setInput] = useState('');

  const clientRef = useRef<Client | null>(null);
  const chatSubRef = useRef<StompSubscription | null>(null);
  const retriedRef = useRef(false);
  const listRef = useRef<HTMLUListElement | null>(null);

  const push = (item: ChatItem) => setItems((prev) => [...prev.slice(-199), item]);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [items]);

  // 새로 연결되거나 방송 ID가 바뀌면 지금 client에 채팅 구독을 건다.
  useEffect(() => {
    const client = clientRef.current;
    if (!connected || !client) return;
    chatSubRef.current?.unsubscribe();
    chatSubRef.current = null;
    if (liveStreamId) {
      chatSubRef.current = client.subscribe(`/topic/live-streams/${liveStreamId}/chat`, onChatMessage);
      log(`채팅 구독: 방송 #${liveStreamId}`);
    }
  }, [connected, connectionGen, liveStreamId]);

  useEffect(
    () => () => {
      clientRef.current?.deactivate();
      clientRef.current = null;
    },
    [],
  );

  function onChatMessage(frame: IMessage) {
    const msg = JSON.parse(frame.body) as ServerMessage;
    if (msg.type === 'SYSTEM') {
      push({ kind: 'system', key: nextKey(), content: msg.content, sentAt: msg.sentAt });
    } else {
      push({
        kind: 'chat',
        key: nextKey(),
        nickname: msg.nickname ?? '',
        senderId: msg.senderId ?? 0,
        content: msg.content,
        sentAt: msg.sentAt,
      });
    }
  }

  function connect() {
    if (!getAccessToken()) {
      log('로그인 후 연결할 수 있어요.', 'error');
      return;
    }
    clientRef.current?.deactivate();
    // 이전 client의 구독은 그 client와 함께 끝난다. 새 client에서 해제하지 않도록 비워 둔다.
    chatSubRef.current = null;

    const client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 0, // 재연결은 에러 코드를 보고 직접 결정한다.
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
    });
    // 재연결할 때마다 최신 Access Token을 담는다.
    client.beforeConnect = () => {
      client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ''}` };
    };
    client.onConnect = () => {
      if (!isCurrent(client)) return;
      retriedRef.current = false;
      setConnected(true);
      setConnectionGen((gen) => gen + 1);
      log('채팅 서버 연결');
      // 내가 보낸 메시지가 거부되면 나에게만 오는 에러
      client.subscribe('/user/queue/errors', (frame) => {
        const err = JSON.parse(frame.body) as { code: string; message: string };
        push({ kind: 'error', key: nextKey(), code: err.code, content: err.message });
      });
    };
    // 연결 거부, 토큰 만료·로그아웃(AUTH_002/004), 허용되지 않은 경로(COMMON_005) 등. 서버가 연결을 끊는다.
    client.onStompError = (frame) => {
      if (!isCurrent(client)) return;
      const code = frame.headers.message ?? 'UNKNOWN';
      let message = frame.body;
      try {
        message = (JSON.parse(frame.body) as { error?: { message?: string } }).error?.message ?? message;
      } catch {
        // 본문이 JSON이 아닌 경우
      }
      push({ kind: 'error', key: nextKey(), code, content: `${message} (연결 종료)` });
      log(`채팅 연결 거부: [${code}] ${message}`, 'error');

      if (code === 'AUTH_002' && !retriedRef.current) {
        retriedRef.current = true;
        log('토큰 만료 → 재발급 후 다시 연결');
        reissue()
          .then(() => {
            // 재발급하는 사이에 연결 해제를 눌렀거나 화면을 떠났으면 다시 연결하지 않는다.
            if (isCurrent(client)) connect();
          })
          .catch(() => log('재발급 실패. 다시 로그인해 주세요.', 'error'));
      }
    };
    client.onWebSocketClose = () => {
      if (!isCurrent(client)) return;
      setConnected(false);
      chatSubRef.current = null;
    };

    clientRef.current = client;
    client.activate();
  }

  // 재연결로 client가 바뀐 뒤에도 이전 client의 콜백이 늦게 실행될 수 있다.
  // 지금 쓰는 client의 콜백일 때만 화면 상태를 바꾼다.
  function isCurrent(client: Client): boolean {
    return clientRef.current === client;
  }

  function disconnect() {
    clientRef.current?.deactivate();
    clientRef.current = null;
    setConnected(false);
    log('채팅 연결 해제');
  }

  function send(e: FormEvent) {
    e.preventDefault();
    const client = clientRef.current;
    if (!client || !connected || !liveStreamId) return;
    client.publish({
      destination: `/app/live-streams/${liveStreamId}/chat`,
      body: JSON.stringify({ content: input }),
    });
    setInput('');
  }

  return (
    <section className="panel chat">
      <div className="row between">
        <h2>채팅 {liveStreamId ? `· 방송 #${liveStreamId}` : ''}</h2>
        {connected ? (
          <button type="button" onClick={disconnect}>연결 해제</button>
        ) : (
          <button type="button" className="primary" onClick={connect}>채팅 연결</button>
        )}
      </div>
      <p className="meta">
        상태: <strong>{connected ? '연결됨' : '연결 안 됨'}</strong>
        {!liveStreamId && ' · 방송 ID를 먼저 정해 주세요'}
      </p>

      <ul ref={listRef} className="messages" aria-live="polite">
        {items.map((item) => {
          if (item.kind === 'system') {
            return <li key={item.key} className="system">{item.content}</li>;
          }
          if (item.kind === 'error') {
            return (
              <li key={item.key} className="error-msg">
                <strong>{item.code}</strong> {item.content}
              </li>
            );
          }
          return (
            <li key={item.key}>
              <strong>{item.nickname}</strong>
              <span className="sub"> #{item.senderId}</span> {item.content}
            </li>
          );
        })}
      </ul>

      <form onSubmit={send} className="row">
        <label className="grow">
          <span className="sr-only">채팅 입력</span>
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            maxLength={200}
            placeholder={connected ? '메시지 입력 (최대 200자)' : '먼저 채팅 연결을 눌러 주세요'}
            disabled={!connected || !liveStreamId}
          />
        </label>
        <span className="meta">{input.length}/200</span>
        <button type="submit" className="primary" disabled={!connected || !liveStreamId}>전송</button>
      </form>
    </section>
  );
}
