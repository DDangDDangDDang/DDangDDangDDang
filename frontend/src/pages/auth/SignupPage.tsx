import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { api, call, errorMessage, login, useSession } from '@/api';
import { VerificationField } from '@/components/auth/VerificationField';
import { AuthCard } from '@/components/layout/PageHeader';
import { Button, Input, Notice } from '@/components/ui';

export function SignupPage() {
  const session = useSession();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  // 휴대폰 인증을 마치면 서버가 준 인증 토큰과 인증한 번호를 둔다. 번호를 바꾸면 다시 인증해야 한다.
  const [verified, setVerified] = useState<{ phoneNumber: string; token: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  if (session) return <Navigate to="/" replace />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!verified) return;
    setSubmitting(true);
    setError(undefined);
    try {
      await call(
        api.POST('/api/v1/auth/signup', {
          body: { email, password, nickname, phoneNumber: verified.phoneNumber, phoneVerificationToken: verified.token },
        }),
      );
      // 가입하면 바로 로그인한다.
      await login({ email, password });
      navigate('/', { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  const canSubmit = Boolean(verified && email && password && nickname);

  return (
    <AuthCard
      title="회원가입"
      footer={
        <>
          이미 계정이 있나요?
          <Link to="/login" className="font-semibold text-neutral-900">
            로그인
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="이메일" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Input
          label="비밀번호"
          type="password"
          autoComplete="new-password"
          hint="8~20자, 영문·숫자·특수문자를 모두 포함해 주세요."
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          maxLength={20}
          required
        />
        <Input
          label="닉네임"
          autoComplete="nickname"
          hint="2~10자로 입력해 주세요."
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          maxLength={10}
          required
        />

        <VerificationField
          kind="phone"
          verified={verified !== null}
          onTargetChange={() => setVerified(null)}
          sendCode={async (phoneNumber) => {
            const data = await call(api.POST('/api/v1/auth/signup/phone/code', { body: { phoneNumber } }));
            return data?.expiresIn ?? 300;
          }}
          verify={async (phoneNumber, code) => {
            const data = await call(api.POST('/api/v1/auth/signup/phone/verify', { body: { phoneNumber, code } }));
            if (data?.verificationToken) setVerified({ phoneNumber, token: data.verificationToken });
          }}
          verifiedMessage="휴대폰 인증을 완료했어요."
        />

        {error && <Notice tone="error">{error}</Notice>}
        <Button type="submit" size="lg" loading={submitting} disabled={!canSubmit}>
          가입하기
        </Button>
        {!verified && <p className="text-center text-caption text-neutral-500">휴대폰 인증을 마치면 가입할 수 있어요.</p>}
      </form>
    </AuthCard>
  );
}
