import { FormEvent, useState } from 'react';
import { errorText, login, signup } from './api';
import type { Log } from './App';

type Props = { log: Log };

export default function AuthPanel({ log }: Props) {
  const [mode, setMode] = useState<'login' | 'signup'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'signup') {
        await signup(email, password, nickname);
        log(`회원가입 완료: ${email}`);
      }
      const res = await login(email, password);
      log(`로그인: ${res.user.nickname} (id=${res.user.userId})`);
    } catch (err) {
      setError(errorText(err));
      log(errorText(err), 'error');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel auth">
      <h2>{mode === 'login' ? '로그인' : '회원가입'}</h2>
      <form onSubmit={onSubmit} className="stack">
        <label>
          이메일
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label>
          비밀번호
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          />
        </label>
        {mode === 'signup' && (
          <>
            <span className="hint">8~20자, 영문·숫자·특수문자 포함</span>
            <label>
              닉네임 (2~10자)
              <input value={nickname} onChange={(e) => setNickname(e.target.value)} required />
            </label>
          </>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="primary" disabled={busy}>
          {mode === 'login' ? '로그인' : '가입하고 로그인'}
        </button>
      </form>
      <button type="button" className="link" onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}>
        {mode === 'login' ? '계정이 없으면 회원가입' : '이미 계정이 있으면 로그인'}
      </button>
    </section>
  );
}
