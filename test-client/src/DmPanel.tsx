import { FormEvent, useCallback, useEffect, useRef, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { dmApi, DmEvent, DmMessage, DmReportReason, DmRoom, errorText, getAccessToken, reissue } from './api';
import type { Log } from './App';

type Props = { log: Log; userId: number };

// 서버 응답을 기다리는 내 메시지. 실패하면 같은 clientMessageId로 다시 보내 한 번만 저장되게 한다.
type Pending = { clientMessageId: string; content: string; error?: string };

const REPORT_REASONS: [DmReportReason, string][] = [
  ['EXTERNAL_TRADE', '외부 거래 유도'],
  ['SCAM', '사기·사칭'],
  ['HARASSMENT', '욕설·괴롭힘'],
  ['SEXUAL', '성적인 내용'],
  ['SPAM', '광고·스팸'],
  ['OTHER', '기타'],
];

function wsUrl(): string {
  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${protocol}://${window.location.host}/ws`;
}

function timeText(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
}

export default function DmPanel({ log, userId }: Props) {
  const [connected, setConnected] = useState(false);
  const [rooms, setRooms] = useState<DmRoom[]>([]);
  const [roomsCursor, setRoomsCursor] = useState<number | null>(null);
  const [totalUnread, setTotalUnread] = useState(0);
  const [targetInput, setTargetInput] = useState('');

  const [current, setCurrent] = useState<DmRoom | null>(null);
  const [messages, setMessages] = useState<DmMessage[]>([]); // 오래된 → 최신 순
  const [olderCursor, setOlderCursor] = useState<number | null>(null);
  const [pending, setPending] = useState<Pending[]>([]);
  const [input, setInput] = useState('');

  // 신고 폼. messageId가 없으면 대화 전체(상대) 신고
  const [reportTarget, setReportTarget] = useState<{ messageId?: number } | null>(null);
  const [reportReason, setReportReason] = useState<DmReportReason>('EXTERNAL_TRADE');
  const [reportDetail, setReportDetail] = useState('');

  const clientRef = useRef<Client | null>(null);
  const retriedRef = useRef(false);
  // STOMP 콜백은 처음 만든 함수를 계속 쓰므로, 지금 열린 방은 ref로 읽는다.
  const currentRef = useRef<DmRoom | null>(null);
  const listRef = useRef<HTMLUListElement | null>(null);

  useEffect(() => {
    currentRef.current = current;
  }, [current]);

  const refreshRooms = useCallback(async () => {
    try {
      const [page, unread] = await Promise.all([dmApi.rooms(), dmApi.unreadCount()]);
      setRooms(page.content);
      setRoomsCursor(page.nextCursor);
      setTotalUnread(unread.unreadCount);
    } catch (e) {
      log(`1:1 대화방 목록 조회 실패: ${errorText(e)}`, 'error');
    }
  }, [log]);

  async function loadMoreRooms() {
    if (!roomsCursor) return;
    try {
      const page = await dmApi.rooms(roomsCursor);
      setRooms((prev) => [...prev, ...page.content]);
      setRoomsCursor(page.nextCursor);
    } catch (e) {
      log(`1:1 대화방 더 보기 실패: ${errorText(e)}`, 'error');
    }
  }

  function addMessage(message: DmMessage) {
    setMessages((prev) =>
      prev.some((m) => m.messageId === message.messageId)
        ? prev
        : [...prev, message].sort((a, b) => a.messageId - b.messageId),
    );
    setPending((prev) => prev.filter((p) => p.clientMessageId !== message.clientMessageId));
  }

  async function markRead(roomId: number) {
    try {
      const res = await dmApi.read(roomId);
      setRooms((prev) => prev.map((r) => (r.roomId === roomId ? { ...r, unreadCount: res.unreadCount } : r)));
      setTotalUnread((await dmApi.unreadCount()).unreadCount);
    } catch (e) {
      log(`읽음 처리 실패: ${errorText(e)}`, 'error');
    }
  }

  // 대화방을 열면 최근 메시지를 불러오고 읽음 처리한다.
  async function openRoom(otherUserId: number) {
    try {
      const room = await dmApi.openRoom(otherUserId);
      const page = await dmApi.messages(room.roomId);
      setCurrent(room);
      setMessages([...page.content].reverse());
      setOlderCursor(page.nextCursor);
      setPending([]);
      setReportTarget(null);
      log(`1:1 대화방 열기: #${room.roomId} (상대 ${room.otherUser.nickname ?? '알 수 없음'} #${otherUserId})`);
      await markRead(room.roomId);
      requestAnimationFrame(() => listRef.current?.scrollTo({ top: listRef.current.scrollHeight }));
    } catch (e) {
      log(`1:1 대화방 열기 실패: ${errorText(e)}`, 'error');
    }
  }

  async function loadOlder() {
    if (!current || !olderCursor) return;
    try {
      const page = await dmApi.messages(current.roomId, olderCursor);
      setMessages((prev) => [...[...page.content].reverse(), ...prev]);
      setOlderCursor(page.nextCursor);
    } catch (e) {
      log(`이전 메시지 조회 실패: ${errorText(e)}`, 'error');
    }
  }

  async function sendPending(room: DmRoom, item: Pending) {
    setPending((prev) => prev.map((p) => (p.clientMessageId === item.clientMessageId ? { ...p, error: undefined } : p)));
    try {
      addMessage(await dmApi.send(room.roomId, item.clientMessageId, item.content));
      requestAnimationFrame(() => listRef.current?.scrollTo({ top: listRef.current.scrollHeight }));
    } catch (e) {
      setPending((prev) =>
        prev.map((p) => (p.clientMessageId === item.clientMessageId ? { ...p, error: errorText(e) } : p)),
      );
      log(`1:1 메시지 전송 실패: ${errorText(e)}`, 'error');
    }
  }

  function send(e: FormEvent) {
    e.preventDefault();
    if (!current || !input.trim()) return;
    const item: Pending = { clientMessageId: crypto.randomUUID(), content: input };
    setPending((prev) => [...prev, item]);
    setInput('');
    void sendPending(current, item);
  }

  async function toggleBlock() {
    if (!current) return;
    const other = current.otherUser;
    try {
      if (current.blockedByMe) {
        await dmApi.unblock(other.userId);
        log(`차단 해제: #${other.userId}`);
      } else {
        await dmApi.block(other.userId);
        log(`차단: #${other.userId} (서로 메시지를 보낼 수 없어요)`);
      }
      setCurrent(await dmApi.openRoom(other.userId));
      await refreshRooms();
    } catch (e) {
      log(`차단 변경 실패: ${errorText(e)}`, 'error');
    }
  }

  async function submitReport(e: FormEvent) {
    e.preventDefault();
    if (!current || !reportTarget) return;
    try {
      await dmApi.report(current.roomId, reportReason, reportTarget.messageId, reportDetail);
      log(`신고 접수: ${reportTarget.messageId ? `메시지 #${reportTarget.messageId}` : '대화 전체'}`);
      setReportTarget(null);
      setReportDetail('');
    } catch (err) {
      log(`신고 실패: ${errorText(err)}`, 'error');
    }
  }

  // 실시간 이벤트: 열린 방이면 화면에 바로 반영하고, 그 밖의 방은 목록을 다시 불러온다.
  function onEvent(event: DmEvent) {
    const open = currentRef.current;
    if (event.type === 'MESSAGE') {
      if (open?.roomId === event.roomId) {
        addMessage(event.message);
        if (event.message.senderId !== userId) void markRead(event.roomId);
        requestAnimationFrame(() => listRef.current?.scrollTo({ top: listRef.current.scrollHeight }));
      }
      if (event.message.senderId !== userId) log(`1:1 새 메시지: 대화방 #${event.roomId}`);
      void refreshRooms();
    } else if (event.readerId !== userId) {
      // 상대가 읽음 → 내 메시지의 "1"을 지운다.
      if (open?.roomId === event.roomId) {
        setCurrent((prev) => (prev ? { ...prev, otherLastReadMessageId: event.lastReadMessageId } : prev));
      }
    } else {
      // 내 다른 탭에서 읽음 → 읽지 않은 수만 다시 불러온다.
      void refreshRooms();
    }
  }
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  const connect = useCallback(() => {
    if (!getAccessToken()) return;
    clientRef.current?.deactivate();
    const client = new Client({ brokerURL: wsUrl(), reconnectDelay: 0, heartbeatIncoming: 10000, heartbeatOutgoing: 10000 });
    const isCurrent = () => clientRef.current === client;
    client.beforeConnect = () => {
      client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ''}` };
    };
    client.onConnect = () => {
      if (!isCurrent()) return;
      retriedRef.current = false;
      setConnected(true);
      client.subscribe('/user/queue/dm', (frame) => onEventRef.current(JSON.parse(frame.body) as DmEvent));
      log('1:1 채팅 실시간 연결');
    };
    client.onStompError = (frame) => {
      if (!isCurrent()) return;
      const code = frame.headers.message ?? 'UNKNOWN';
      log(`1:1 채팅 연결 거부: [${code}]`, 'error');
      if (code === 'AUTH_002' && !retriedRef.current) {
        retriedRef.current = true;
        reissue()
          .then(() => {
            if (isCurrent()) connect();
          })
          .catch(() => log('재발급 실패. 다시 로그인해 주세요.', 'error'));
      }
    };
    client.onWebSocketClose = () => {
      if (isCurrent()) setConnected(false);
    };
    clientRef.current = client;
    client.activate();
  }, [log]);

  // 로그인한 동안 자동으로 연결하고 목록을 불러온다.
  useEffect(() => {
    connect();
    void refreshRooms();
    return () => {
      clientRef.current?.deactivate();
      clientRef.current = null;
    };
  }, [connect, refreshRooms]);

  function startConversation(e: FormEvent) {
    e.preventDefault();
    const id = Number(targetInput);
    if (!Number.isInteger(id) || id <= 0) {
      log('대화할 회원 ID를 입력해 주세요.', 'error');
      return;
    }
    void openRoom(id);
  }

  const otherRead = current?.otherLastReadMessageId ?? 0;

  return (
    <section className="panel dm">
      <div className="row between">
        <h2>
          1:1 채팅 {totalUnread > 0 && <span className="badge">{totalUnread}</span>}
        </h2>
        <span className="meta">
          실시간: <strong>{connected ? '연결됨' : '연결 안 됨'}</strong>{' '}
          {!connected && <button type="button" onClick={connect}>다시 연결</button>}{' '}
          <button type="button" onClick={() => void refreshRooms()}>목록 새로고침</button>
        </span>
      </div>

      <div className="dm-layout">
        <div className="dm-rooms">
          <form className="row" onSubmit={startConversation}>
            <label className="grow">
              상대 회원 ID
              <input value={targetInput} onChange={(e) => setTargetInput(e.target.value)} inputMode="numeric" />
            </label>
            <button type="submit" className="primary">대화 시작</button>
          </form>

          <ul className="room-list">
            {rooms.length === 0 && <li className="meta">메시지를 주고받은 대화방이 없어요</li>}
            {rooms.map((room) => (
              <li key={room.roomId}>
                <button
                  type="button"
                  className={`room-item${current?.roomId === room.roomId ? ' active' : ''}`}
                  onClick={() => void openRoom(room.otherUser.userId)}
                >
                  <span className="row between">
                    <strong>
                      {room.otherUser.nickname ?? '(알 수 없음)'}
                      <span className="sub"> #{room.otherUser.userId}</span>
                      {room.blockedByMe && <span className="sub"> · 차단함</span>}
                    </strong>
                    {room.unreadCount > 0 && <span className="badge">{room.unreadCount}</span>}
                  </span>
                  <span className="sub preview">{room.lastMessagePreview}</span>
                </button>
              </li>
            ))}
          </ul>
          {roomsCursor && <button type="button" onClick={() => void loadMoreRooms()}>대화방 더 보기</button>}
        </div>

        <div className="dm-chat">
          {!current ? (
            <p className="meta">왼쪽에서 대화방을 고르거나 상대 회원 ID로 대화를 시작해 주세요.</p>
          ) : (
            <>
              <div className="row between">
                <strong>
                  {current.otherUser.nickname ?? '(알 수 없음)'}
                  <span className="sub"> #{current.otherUser.userId} · 대화방 #{current.roomId}</span>
                </strong>
                <span className="row">
                  <button type="button" onClick={() => void toggleBlock()}>
                    {current.blockedByMe ? '차단 해제' : '차단'}
                  </button>
                  <button type="button" onClick={() => setReportTarget({})}>대화 신고</button>
                </span>
              </div>

              {reportTarget && (
                <form className="report-box" onSubmit={submitReport}>
                  <strong>{reportTarget.messageId ? `메시지 #${reportTarget.messageId} 신고` : '대화 상대 신고'}</strong>
                  <div className="row">
                    <label>
                      사유
                      <select value={reportReason} onChange={(e) => setReportReason(e.target.value as DmReportReason)}>
                        {REPORT_REASONS.map(([value, label]) => (
                          <option key={value} value={value}>{label}</option>
                        ))}
                      </select>
                    </label>
                    <label className="grow">
                      내용 (선택)
                      <input value={reportDetail} onChange={(e) => setReportDetail(e.target.value)} maxLength={500} />
                    </label>
                    <button type="submit" className="primary">신고하기</button>
                    <button type="button" onClick={() => setReportTarget(null)}>취소</button>
                  </div>
                </form>
              )}

              <ul ref={listRef} className="messages dm-messages" aria-live="polite">
                {olderCursor && (
                  <li className="system">
                    <button type="button" className="link" onClick={() => void loadOlder()}>이전 메시지 더 보기</button>
                  </li>
                )}
                {messages.map((m) => {
                  const mine = m.senderId === userId;
                  return (
                    <li key={m.messageId} className={mine ? 'mine' : 'theirs'}>
                      <span className="bubble">{m.content}</span>
                      <span className="sub">
                        {mine && m.messageId > otherRead && <strong className="unread-mark">1 </strong>}
                        {timeText(m.sentAt)}
                        {!mine && (
                          <>
                            {' · '}
                            <button type="button" className="link" onClick={() => setReportTarget({ messageId: m.messageId })}>
                              신고
                            </button>
                          </>
                        )}
                      </span>
                    </li>
                  );
                })}
                {pending.map((p) => (
                  <li key={p.clientMessageId} className="mine">
                    <span className="bubble pending">{p.content}</span>
                    {p.error ? (
                      <span className="error-msg">
                        {p.error}{' '}
                        <button type="button" className="link" onClick={() => current && void sendPending(current, p)}>다시 보내기</button>{' '}
                        <button
                          type="button"
                          className="link"
                          onClick={() => setPending((prev) => prev.filter((x) => x.clientMessageId !== p.clientMessageId))}
                        >
                          지우기
                        </button>
                      </span>
                    ) : (
                      <span className="sub">보내는 중…</span>
                    )}
                  </li>
                ))}
              </ul>

              <form onSubmit={send} className="row">
                <label className="grow">
                  <span className="sr-only">1:1 메시지 입력</span>
                  <input
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    maxLength={1000}
                    placeholder={current.blockedByMe ? '차단을 해제해야 보낼 수 있어요' : '메시지 입력 (최대 1000자)'}
                    disabled={current.blockedByMe}
                  />
                </label>
                <button type="submit" className="primary" disabled={current.blockedByMe}>전송</button>
              </form>
            </>
          )}
        </div>
      </div>
    </section>
  );
}
