import { useCallback, useEffect, useState } from 'react';
import { errorText, logout, onSessionChange, reissue, UserInfo } from './api';
import AuthPanel from './AuthPanel';
import LivePanel from './LivePanel';
import ChatPanel from './ChatPanel';

export type Log = (message: string, level?: 'info' | 'error') => void;
type LogEntry = { key: number; time: string; message: string; level: 'info' | 'error' };

let logSeq = 0;

export default function App() {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [restoring, setRestoring] = useState(true);
  const [liveStreamId, setLiveStreamId] = useState<number | null>(null);
  const [logs, setLogs] = useState<LogEntry[]>([]);

  const log = useCallback<Log>((message, level = 'info') => {
    const time = new Date().toLocaleTimeString('ko-KR', { hour12: false });
    setLogs((prev) => [{ key: ++logSeq, time, message, level }, ...prev].slice(0, 200));
  }, []);

  // 로그인·재발급·로그아웃 결과를 화면에 반영한다.
  useEffect(() => onSessionChange((session) => setUser(session?.user ?? null)), []);

  // 새로고침하면 메모리의 Access Token이 사라지므로 Refresh Token 쿠키로 로그인 상태를 복구한다.
  useEffect(() => {
    reissue()
      .then((res) => log(`로그인 상태 복구: ${res.user.nickname}`))
      .catch(() => undefined)
      .finally(() => setRestoring(false));
  }, [log]);

  async function onLogout() {
    try {
      await logout();
      log('로그아웃');
    } catch (e) {
      log(`로그아웃 실패: ${errorText(e)}`, 'error');
    }
  }

  return (
    <div className="app">
      <header className="topbar">
        <h1>땅땅땅 테스트 클라이언트</h1>
        {user && (
          <div className="row">
            <span>
              <strong>{user.nickname}</strong> · id {user.userId} · {user.role}
            </span>
            <button type="button" onClick={onLogout}>로그아웃</button>
          </div>
        )}
      </header>

      {restoring && <p className="meta">로그인 상태 확인 중…</p>}
      {!restoring && !user && <AuthPanel log={log} />}

      {user && (
        <main className="grid">
          <LivePanel log={log} liveStreamId={liveStreamId} onLiveStreamIdChange={setLiveStreamId} />
          <ChatPanel log={log} liveStreamId={liveStreamId} />
        </main>
      )}

      <section className="panel logs">
        <div className="row between">
          <h2>로그</h2>
          <button type="button" onClick={() => setLogs([])}>지우기</button>
        </div>
        <ol>
          {logs.map((entry) => (
            <li key={entry.key} className={entry.level === 'error' ? 'error-msg' : undefined}>
              <span className="sub">{entry.time}</span> {entry.message}
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
