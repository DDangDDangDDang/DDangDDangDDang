import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router';
import { errorMessage, login, useSession } from '@/api';
import { AuthCard } from '@/components/layout/PageHeader';
import { Button, Input, Notice } from '@/components/ui';
import { safeRedirect } from '@/lib/redirect';
import { OrDivider, SocialLoginButtons } from '@/components/auth/SocialLoginButtons';

/** 다른 화면에서 로그인 화면으로 보낼 때 함께 보여줄 안내 (예: 비밀번호 변경 완료) */
export type LoginLocationState = { notice?: string } | null;

export function LoginPage() {
  const session = useSession();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const notice = (useLocation().state as LoginLocationState)?.notice;
  const redirect = safeRedirect(params.get('redirect'));

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string>();

  if (session) return <Navigate to={redirect} replace />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError(undefined);
    try {
      await login({ email, password });
      navigate(redirect, { replace: true });
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard
      title="로그인"
      footer={
        <>
          <Link to="/find-email" className="hover:text-neutral-900">
            이메일 찾기
          </Link>
          <span aria-hidden="true">·</span>
          <Link to="/password-reset" className="hover:text-neutral-900">
            비밀번호 재설정
          </Link>
          <span aria-hidden="true">·</span>
          {/* 가입한 뒤에도 보던 화면으로 돌아가도록 redirect를 넘긴다. */}
          <Link to={redirect === '/' ? '/signup' : `/signup?redirect=${encodeURIComponent(redirect)}`} className="font-semibold text-neutral-900">
            회원가입
          </Link>
        </>
      }
    >
      {notice && <Notice tone="success">{notice}</Notice>}
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input label="이메일" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <Input
          label="비밀번호"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
        {error && <Notice tone="error">{error}</Notice>}
        <Button type="submit" size="lg" loading={submitting} disabled={!email || !password}>
          로그인
        </Button>
      </form>
      <OrDivider />
      <SocialLoginButtons redirect={redirect} />
    </AuthCard>
  );
}
