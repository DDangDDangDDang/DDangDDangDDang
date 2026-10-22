import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { api, call, errorMessage, isErrorCode, login, useSession } from '@/api';
import { VerificationField } from '@/components/auth/VerificationField';
import { AuthCard } from '@/components/layout/PageHeader';
import { Button, Input, Notice } from '@/components/ui';
import { safeRedirect } from '@/lib/redirect';
import type { LoginLocationState } from './LoginPage';

export function SignupPage() {
  const session = useSession();
  const navigate = useNavigate();
  // 로그인이 필요한 동작(채팅·입찰 등)에서 회원가입으로 왔으면, 가입하고 그 화면으로 돌아간다.
  const [params] = useSearchParams();
  const redirect = safeRedirect(params.get('redirect'));

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [nickname, setNickname] = useState('');
  // 휴대폰 인증을 마치면 서버가 준 인증 토큰과 인증한 번호를 둔다. 번호를 바꾸면 다시 인증해야 한다.
  const [verified, setVerified] = useState<{ phoneNumber: string; token: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  if (session) return <Navigate to={redirect} replace />;

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
    } catch (e) {
      setError(errorMessage(e));
      // 이미 가입된 번호(USER_004)이거나 인증이 만료·사용됨(VERIFY_004)이면 인증 토큰을 다시 쓸 수 없으므로 휴대폰 인증을 다시 연다.
      // 이메일·닉네임 중복(USER_001·002)은 서버가 토큰을 쓰기 전에 검사하므로 인증을 그대로 둔다.
      if (isErrorCode(e, 'USER_004') || isErrorCode(e, 'VERIFY_004')) setVerified(null);
      setSubmitting(false);
      return;
    }

    // 가입하면 바로 로그인한다. 로그인만 실패하면 가입은 끝났으므로 로그인 화면으로 보낸다. (다시 가입하지 않게)
    try {
      await login({ email, password });
      navigate(redirect, { replace: true });
    } catch {
      const state: LoginLocationState = { notice: '가입을 완료했어요. 로그인해 주세요.' };
      navigate(redirect === '/' ? '/login' : `/login?redirect=${encodeURIComponent(redirect)}`, { replace: true, state });
    }
  }

  const canSubmit = Boolean(verified && email && password && nickname);

  return (
    <AuthCard
      title="회원가입"
      footer={
        <>
          이미 계정이 있나요?
          <Link to={redirect === '/' ? '/login' : `/login?redirect=${encodeURIComponent(redirect)}`} className="font-semibold text-neutral-900">
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
            if (data?.verificationToken) {
              setVerified({ phoneNumber, token: data.verificationToken });
              // 다시 인증했으면 이전 가입 실패 안내(인증 만료 등)를 지운다.
              setError(undefined);
            }
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
