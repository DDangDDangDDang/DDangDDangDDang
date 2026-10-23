import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { api, call, errorMessage, isErrorCode, kakaoSignup } from '@/api';
import { KakaoLoginButton } from '@/components/auth/KakaoLoginButton';
import { VerificationField } from '@/components/auth/VerificationField';
import { AuthCard } from '@/components/layout/PageHeader';
import { Button, Input, Notice, buttonVariants } from '@/components/ui';

/** 카카오 콜백에서 이 화면으로 넘겨주는 값. 새로고침하면 사라지므로(history state) 없으면 카카오 로그인부터 다시 한다. */
export type KakaoSignupLocationState = {
    signupToken: string;
    /** 카카오가 검증한 이메일. 없으면 빈 문자열 */
    email: string;
    /** 카카오 닉네임 (추천값) */
    nickname: string;
    redirect: string;
};

/**
 * 카카오로 처음 온 사용자의 추가 정보: 닉네임, 이메일, 휴대폰 인증.
 * 인증한 번호로 이미 가입한 계정이 있으면 서버가 그 계정에 카카오를 연결한다. (닉네임·이메일은 쓰지 않음)
 */
export function KakaoSignupPage() {
    const navigate = useNavigate();
    const pending = useLocation().state as KakaoSignupLocationState | null;

    const [nickname, setNickname] = useState(pending?.nickname ?? '');
    const [email, setEmail] = useState(pending?.email ?? '');
    // 휴대폰 인증을 마치면 서버가 준 인증 토큰과 인증한 번호를 둔다. 번호를 바꾸면 다시 인증해야 한다.
    const [verified, setVerified] = useState<{ phoneNumber: string; token: string } | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string>();
    // 가입 대기 시간(10분)이 지나면 카카오 로그인부터 다시 해야 한다
    const [expired, setExpired] = useState(false);
    // 기존 계정에 연결됐으면 안내를 보여주고 [계속하기]로 이동한다
    const [linkedNickname, setLinkedNickname] = useState<string>();

    if (!pending?.signupToken) {
        return (
            <AuthCard title="카카오로 가입하기">
                <Notice tone="info">가입 정보가 없어요. 카카오 로그인부터 다시 진행해 주세요.</Notice>
                <Link to="/login" replace className={buttonVariants({ variant: 'outline' })}>
                    로그인 화면으로
                </Link>
            </AuthCard>
        );
    }
    const { signupToken, redirect } = pending;

    if (linkedNickname !== undefined) {
        return (
            <AuthCard title="카카오 연결 완료">
                <Notice tone="success">
                    이 휴대폰 번호로 가입한 계정({linkedNickname})이 있어서, 그 계정에 카카오를 연결했어요. 다음부터는 카카오로 바로 로그인할 수 있어요.
                </Notice>
                <Button size="lg" onClick={() => navigate(redirect, { replace: true })}>
                    계속하기
                </Button>
            </AuthCard>
        );
    }

    async function handleSubmit(event: FormEvent) {
        event.preventDefault();
        if (!verified) return;
        setSubmitting(true);
        setError(undefined);
        try {
            const data = await kakaoSignup({
                signupToken,
                nickname,
                email,
                phoneNumber: verified.phoneNumber,
                phoneVerificationToken: verified.token,
            });
            if (data?.linkedExistingAccount) {
                setLinkedNickname(data.user?.nickname ?? '');
            } else {
                navigate(redirect, { replace: true });
            }
        } catch (e) {
            setError(errorMessage(e));
            if (isErrorCode(e, 'OAUTH_003')) setExpired(true);
            // 인증이 만료·사용됐거나(VERIFY_004), 그 번호의 계정에 다른 카카오가 연결돼 있으면(OAUTH_004) 다른 번호로 다시 인증하게 연다.
            // 이메일·닉네임 중복(USER_001·002)은 서버가 토큰을 쓰기 전에 검사하므로 인증을 그대로 둔다.
            if (isErrorCode(e, 'VERIFY_004') || isErrorCode(e, 'OAUTH_004')) setVerified(null);
        } finally {
            setSubmitting(false);
        }
    }

    if (expired) {
        return (
            <AuthCard title="카카오로 가입하기">
                <Notice tone="error">{error}</Notice>
                <KakaoLoginButton redirect={redirect} />
            </AuthCard>
        );
    }

    const canSubmit = Boolean(verified && nickname.trim() && email.trim());

    return (
        <AuthCard title="카카오로 가입하기" description="처음 오셨네요. 몇 가지만 더 입력하면 가입이 끝나요.">
            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                <Input
                    label="닉네임"
                    autoComplete="nickname"
                    hint="2~10자로 입력해 주세요."
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    maxLength={10}
                    required
                />
                <Input
                    label="이메일"
                    type="email"
                    autoComplete="email"
                    hint={pending.email ? '카카오 계정의 이메일이에요. 바꿔도 돼요.' : '이메일 찾기·비밀번호 재설정에 쓰여요.'}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
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
                            setError(undefined);
                        }
                    }}
                    verifiedMessage="휴대폰 인증을 완료했어요."
                />
                <p className="text-caption text-neutral-500">이 번호로 이미 가입한 계정이 있으면 그 계정에 카카오가 연결돼요.</p>

                {error && <Notice tone="error">{error}</Notice>}
                <Button type="submit" size="lg" loading={submitting} disabled={!canSubmit}>
                    가입하기
                </Button>
            </form>
        </AuthCard>
    );
}