import { FormEvent, useEffect, useState} from 'react';
import {ApiError, errorText, login, sendSignupPhoneCode, signup, verifySignupPhone} from './api';
import type {Log} from './App';
import RecoveryPanel from './RecoveryPanel';

type Props = { log: Log };

const RESEND_COOLDOWN_MS = 60_000;

// 회원가입 휴대폰 인증 상태. 번호를 바꾸면 처음(idle)으로 돌아간다.
type PhoneStep =
    | { kind: 'idle' }
    | { kind: 'sent'; expiresAt: number; resendAt: number }
    | { kind: 'verified'; token: string };

export default function AuthPanel({log}: Props) {
  const [mode, setMode] = useState<'login' | 'signup' | 'findEmail' | 'resetPassword'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [code, setCode] = useState('');
  const [phoneStep, setPhoneStep] = useState<PhoneStep>({kind: 'idle'});
  const [now, setNow] = useState(() => Date.now());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 인증번호 유효시간과 재요청 대기시간을 1초마다 다시 그린다.
  useEffect(() => {
    if (phoneStep.kind !== 'sent') return;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [phoneStep.kind]);

  function resetPhone() {
    setPhoneStep({kind: 'idle'});
    setCode('');
  }

  function changeMode() {
    setMode(mode === 'login' ? 'signup' : 'login');
    setError(null);
    resetPhone();
  }

  async function onSendCode() {
    setBusy(true);
    setError(null);
    try {
      const res = await sendSignupPhoneCode(phoneNumber);
      const sentAt = Date.now();
      setPhoneStep({kind: 'sent', expiresAt: sentAt + res.expiresIn * 1000, resendAt: sentAt + RESEND_COOLDOWN_MS});
      setNow(sentAt);
      setCode('');
      log(`인증번호 발송: ${phoneNumber} (로컬은 백엔드 로그의 [문자 발송 생략]에서 확인)`);
    } catch (err) {
      setError(errorText(err));
      log(errorText(err), 'error');
    } finally {
      setBusy(false);
    }
  }

  async function onVerifyCode() {
    setBusy(true);
    setError(null);
    try {
      const res = await verifySignupPhone(phoneNumber, code);
      setPhoneStep({kind: 'verified', token: res.verificationToken});
      log(`휴대폰 인증 완료: ${phoneNumber}`);
    } catch (err) {
      setError(errorText(err));
      log(errorText(err), 'error');
    } finally {
      setBusy(false);
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'signup') {
        if (phoneStep.kind !== 'verified') {
          throw new Error('휴대폰 인증을 먼저 완료해 주세요.');
        }
        await signup(email, password, nickname, phoneNumber, phoneStep.token);
        log(`회원가입 완료: ${email}`);
        resetPhone();
      }
      const res = await login(email, password);
      log(`로그인: ${res.user.nickname} (id=${res.user.userId})`);
    } catch (err) {
      // 인증 토큰은 1회용·10분이라, 만료되거나 이미 쓰였으면 다시 인증받게 한다.
      if (err instanceof ApiError && err.code === 'VERIFY_004') resetPhone();
      setError(errorText(err));
      log(errorText(err), 'error');
    } finally {
      setBusy(false);
    }
  }

  if (mode === 'findEmail' || mode === 'resetPassword') {
    return (
        <RecoveryPanel
            kind={mode}
            log={log}
            onBack={(foundEmail) => {
              if (foundEmail) setEmail(foundEmail);
              setPassword('');
              setError(null);
              setMode('login');
            }}
        />
    );
  }

  const remainSec = phoneStep.kind === 'sent' ? Math.max(0, Math.ceil((phoneStep.expiresAt - now) / 1000)) : 0;
  const resendSec = phoneStep.kind === 'sent' ? Math.max(0, Math.ceil((phoneStep.resendAt - now) / 1000)) : 0;
  const phoneValid = /^01[016789]\d{7,8}$/.test(phoneNumber);

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

                <div className="row">
                  <label className="grow">
                    휴대폰 번호 (하이픈 없이 숫자만)
                    <input
                        value={phoneNumber}
                        onChange={(e) => {
                          setPhoneNumber(e.target.value.replace(/\D/g, ''));
                          resetPhone();
                        }}
                        inputMode="numeric"
                        maxLength={11}
                        placeholder="01012345678"
                        autoComplete="tel"
                        required
                    />
                  </label>
                  <button
                      type="button"
                      onClick={onSendCode}
                      disabled={busy || !phoneValid || phoneStep.kind === 'verified' || resendSec > 0}
                  >
                    {phoneStep.kind === 'idle' ? '인증번호 받기' : resendSec > 0 ? `재요청 ${resendSec}초` : '다시 받기'}
                  </button>
                </div>

                {phoneStep.kind === 'sent' && (
                    <>
                      <div className="row">
                        <label className="grow">
                          인증번호 6자리
                          <input
                              value={code}
                              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                              inputMode="numeric"
                              maxLength={6}
                              autoComplete="one-time-code"
                          />
                        </label>
                        <button type="button" onClick={onVerifyCode} disabled={busy || code.length !== 6 || remainSec === 0}>
                          확인
                        </button>
                      </div>
                      <span className="hint">
                  {remainSec > 0
                      ? `남은 시간 ${Math.floor(remainSec / 60)}:${String(remainSec % 60).padStart(2, '0')} · 로컬에서는 백엔드 로그의 [문자 발송 생략]에서 번호 확인`
                      : '인증번호가 만료되었습니다. 다시 받아 주세요.'}
                </span>
                    </>
                )}

                {phoneStep.kind === 'verified' && <span className="hint">휴대폰 인증 완료 ✓ (10분 안에 가입해 주세요)</span>}
              </>
          )}
          {error && <p className="error" role="alert">{error}</p>}
          <button
              type="submit"
              className="primary"
              disabled={busy || (mode === 'signup' && phoneStep.kind !== 'verified')}
          >
            {mode === 'login' ? '로그인' : '가입하고 로그인'}
          </button>
        </form>
        <button type="button" className="link" onClick={changeMode}>
          {mode === 'login' ? '계정이 없으면 회원가입' : '이미 계정이 있으면 로그인'}
        </button>
        {mode === 'login' && (
            <div className="row">
              <button type="button" className="link" onClick={() => setMode('findEmail')}>
                이메일 찾기
              </button>
              <button type="button" className="link" onClick={() => setMode('resetPassword')}>
                비밀번호 재설정
              </button>
            </div>
        )}
      </section>
  );
}