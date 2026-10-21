import { FormEvent, useEffect, useState } from 'react';
import {
    errorText,
    findEmail,
    resetPassword,
    sendFindEmailCode,
    sendPasswordResetCode,
    verifyPasswordResetCode,
    type FoundEmail,
} from './api';
import type { Log } from './App';

type Props = {
    kind: 'findEmail' | 'resetPassword';
    log: Log;
    /** 로그인 화면으로 돌아간다. 비밀번호를 재설정했으면 그 이메일을 넘겨 로그인 칸에 채운다. */
    onBack: (email?: string) => void;
};

const RESEND_COOLDOWN_MS = 60_000;

// input: 번호/이메일 입력 → code: 인증번호 확인 → newPassword: 새 비밀번호(재설정만) → done
type Step =
    | { kind: 'input' }
    | { kind: 'code'; expiresAt: number; resendAt: number }
    | { kind: 'newPassword'; token: string }
    | { kind: 'done'; found?: FoundEmail };

export default function RecoveryPanel({ kind, log, onBack }: Props) {
    const isFindEmail = kind === 'findEmail';
    const [target, setTarget] = useState('');
    const [code, setCode] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [newPasswordConfirm, setNewPasswordConfirm] = useState('');
    const [step, setStep] = useState<Step>({ kind: 'input' });
    const [now, setNow] = useState(() => Date.now());
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (step.kind !== 'code') return;
        const timer = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(timer);
    }, [step.kind]);

    async function run(action: () => Promise<void>) {
        setBusy(true);
        setError(null);
        try {
            await action();
        } catch (err) {
            setError(errorText(err));
            log(errorText(err), 'error');
        } finally {
            setBusy(false);
        }
    }

    function onSendCode() {
        return run(async () => {
            const res = isFindEmail ? await sendFindEmailCode(target) : await sendPasswordResetCode(target);
            const sentAt = Date.now();
            setStep({ kind: 'code', expiresAt: sentAt + res.expiresIn * 1000, resendAt: sentAt + RESEND_COOLDOWN_MS });
            setNow(sentAt);
            setCode('');
            log(`인증번호 발송: ${target} (로컬은 백엔드 로그의 ${isFindEmail ? '[문자 발송 생략]' : '[메일 발송 생략]'}에서 확인)`);
        });
    }

    function onVerifyCode() {
        return run(async () => {
            if (isFindEmail) {
                const found = await findEmail(target, code);
                setStep({ kind: 'done', found });
                log(`이메일 찾기: ${found.maskedEmail}`);
            } else {
                const res = await verifyPasswordResetCode(target, code);
                setStep({ kind: 'newPassword', token: res.verificationToken });
                log(`비밀번호 재설정 인증 완료: ${target}`);
            }
        });
    }

    function onResetPassword(e: FormEvent) {
        e.preventDefault();
        if (step.kind !== 'newPassword') return;
        if (newPassword !== newPasswordConfirm) {
            setError('새 비밀번호가 서로 다릅니다.');
            return;
        }
        return run(async () => {
            await resetPassword(step.token, newPassword);
            setStep({ kind: 'done' });
            log(`비밀번호 재설정 완료: ${target} (기존 로그인 세션은 모두 끊김)`);
        });
    }

    const remainSec = step.kind === 'code' ? Math.max(0, Math.ceil((step.expiresAt - now) / 1000)) : 0;
    const resendSec = step.kind === 'code' ? Math.max(0, Math.ceil((step.resendAt - now) / 1000)) : 0;
    const targetValid = isFindEmail ? /^01[016789]\d{7,8}$/.test(target) : /^\S+@\S+\.\S+$/.test(target);
    const editingTarget = step.kind === 'input' || step.kind === 'code';

    return (
        <section className="panel auth">
            <h2>{isFindEmail ? '이메일 찾기' : '비밀번호 재설정'}</h2>
            <div className="stack">
                {editingTarget && (
                    <>
                        <div className="row">
                            <label className="grow">
                                {isFindEmail ? '가입한 휴대폰 번호 (숫자만)' : '가입한 이메일'}
                                <input
                                    type={isFindEmail ? 'text' : 'email'}
                                    value={target}
                                    onChange={(e) => {
                                        setTarget(isFindEmail ? e.target.value.replace(/\D/g, '') : e.target.value.trim());
                                        setStep({ kind: 'input' });
                                        setCode('');
                                    }}
                                    inputMode={isFindEmail ? 'numeric' : 'email'}
                                    maxLength={isFindEmail ? 11 : 100}
                                    placeholder={isFindEmail ? '01012345678' : 'me@example.com'}
                                />
                            </label>
                            <button type="button" onClick={onSendCode} disabled={busy || !targetValid || resendSec > 0}>
                                {step.kind === 'input' ? '인증번호 받기' : resendSec > 0 ? `재요청 ${resendSec}초` : '다시 받기'}
                            </button>
                        </div>
                        {!isFindEmail && step.kind === 'input' && (
                            <span className="hint">가입하지 않은 이메일이어도 같은 안내가 나옵니다 (계정 존재 여부 비노출).</span>
                        )}
                    </>
                )}

                {step.kind === 'code' && (
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
                  ? `남은 시간 ${Math.floor(remainSec / 60)}:${String(remainSec % 60).padStart(2, '0')} · 로컬에서는 백엔드 로그의 ${isFindEmail ? '[문자 발송 생략]' : '[메일 발송 생략]'}에서 번호 확인`
                  : '인증번호가 만료되었습니다. 다시 받아 주세요.'}
            </span>
                    </>
                )}

                {step.kind === 'newPassword' && (
                    <form onSubmit={onResetPassword} className="stack">
                        <label>
                            새 비밀번호
                            <input
                                type="password"
                                value={newPassword}
                                onChange={(e) => setNewPassword(e.target.value)}
                                required
                                autoComplete="new-password"
                            />
                        </label>
                        <label>
                            새 비밀번호 확인
                            <input
                                type="password"
                                value={newPasswordConfirm}
                                onChange={(e) => setNewPasswordConfirm(e.target.value)}
                                required
                                autoComplete="new-password"
                            />
                        </label>
                        <span className="hint">8~20자, 영문·숫자·특수문자 포함 · 인증 후 10분 안에 변경해 주세요</span>
                        <button type="submit" className="primary" disabled={busy}>
                            비밀번호 변경
                        </button>
                    </form>
                )}

                {step.kind === 'done' &&
                    (step.found ? (
                        <p>
                            가입한 이메일은 <strong>{step.found.maskedEmail}</strong> 입니다. (가입일 {step.found.joinedAt})
                        </p>
                    ) : (
                        <p>비밀번호를 변경했습니다. 다른 기기의 로그인도 모두 끊겼으니 새 비밀번호로 로그인해 주세요.</p>
                    ))}

                {error && <p className="error" role="alert">{error}</p>}
            </div>
            <button type="button" className="link" onClick={() => onBack(step.kind === 'done' && !isFindEmail ? target : undefined)}>
                로그인으로 돌아가기
            </button>
        </section>
    );
}