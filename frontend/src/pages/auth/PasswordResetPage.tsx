import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { api, call, clearSession, errorMessage, useSession } from '@/api';
import { VerificationField } from '@/components/auth/VerificationField';
import { AuthCard } from '@/components/layout/PageHeader';
import { Button, Input, Notice } from '@/components/ui';
import type { LoginLocationState } from './LoginPage';

/**
 * 비밀번호 재설정 (로그인 전, 내 정보의 [비밀번호 변경]에서도 들어온다)
 * 1단계 이메일로 인증번호 받기 → 2단계 인증번호 확인 → 3단계 새 비밀번호
 * 바꾸면 서버가 모든 기기의 로그인을 끊으므로, 이 화면도 로그아웃하고 로그인 화면으로 보낸다.
 */
export function PasswordResetPage() {
  const navigate = useNavigate();
  const [token, setToken] = useState<string | null>(null);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  const mismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;
  const loggedIn = useSession() !== null;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token || mismatch) return;
    setSubmitting(true);
    setError(undefined);
    try {
      await call(api.POST('/api/v1/auth/password-reset', { body: { verificationToken: token, newPassword } }));
      clearSession();
      const state: LoginLocationState = { notice: '비밀번호를 변경했어요. 다시 로그인해 주세요.' };
      navigate('/login', { replace: true, state });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title={loggedIn ? '비밀번호 변경' : '비밀번호 재설정'}
      description="가입한 이메일로 인증번호를 보내 드려요."
    >
      {loggedIn && <Notice tone="info">비밀번호를 바꾸면 모든 기기에서 로그아웃되고 다시 로그인해야 해요.</Notice>}

      <VerificationField
        kind="email"
        verified={token !== null}
        onTargetChange={() => setToken(null)}
        sentMessage="메일을 보냈습니다. 받은 인증번호를 5분 안에 입력해 주세요."
        verifiedMessage="인증을 완료했어요. 새 비밀번호를 입력해 주세요."
        sendCode={async (email) => {
          const data = await call(api.POST('/api/v1/auth/password-reset/code', { body: { email } }));
          return data?.expiresIn ?? 300;
        }}
        verify={async (email, code) => {
          const data = await call(api.POST('/api/v1/auth/password-reset/verify', { body: { email, code } }));
          if (data?.verificationToken) setToken(data.verificationToken);
        }}
      />

      {token && (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4 border-t border-neutral-100 pt-4">
          <Input
            label="새 비밀번호"
            type="password"
            autoComplete="new-password"
            hint="8~20자, 영문·숫자·특수문자를 모두 포함해 주세요."
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            maxLength={20}
            required
          />
          <Input
            label="새 비밀번호 확인"
            type="password"
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            error={mismatch ? '새 비밀번호와 같게 입력해 주세요.' : undefined}
            maxLength={20}
            required
          />
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" size="lg" loading={submitting} disabled={!newPassword || !confirmPassword || mismatch}>
            변경
          </Button>
        </form>
      )}
    </AuthCard>
  );
}
