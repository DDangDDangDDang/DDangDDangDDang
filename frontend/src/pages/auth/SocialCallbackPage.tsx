import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { errorMessage, socialLogin } from '@/api';
import { AuthCard } from '@/components/layout/PageHeader';
import { Notice, Skeleton, buttonVariants } from '@/components/ui';
import { isSocialProvider, SOCIAL_PROVIDER_LABEL, takeSocialState } from '@/lib/social-login';
import { safeRedirect } from '@/lib/redirect';
import type { SocialSignupLocationState } from './SocialSignupPage';

/**
 * 소셜 로그인 콜백 (/oauth/{provider}/callback?code=...&state=...)
 * - state가 시작할 때 저장한 값(제공자 포함)과 다르면 거절한다. (다른 사이트가 만든 로그인 요청 차단)
 * - 연결된 회원이면 로그인하고 원래 보던 화면으로, 처음이면 추가 정보 화면으로 보낸다.
 */
export function SocialCallbackPage() {
    const navigate = useNavigate();
    const { provider } = useParams();
    const [params] = useSearchParams();
    const [error, setError] = useState<string>();
    // 개발 모드(StrictMode)에서 effect가 두 번 돌아도 인가 코드는 한 번만 보낸다. (인가 코드는 1회용)
    const started = useRef(false);
    const label = isSocialProvider(provider) ? SOCIAL_PROVIDER_LABEL[provider] : '소셜';

    useEffect(() => {
        if (started.current) return;
        started.current = true;

        if (!isSocialProvider(provider)) {
            setError('지원하지 않는 로그인 방식이에요.');
            return;
        }
        // 사용자가 제공자 동의 화면에서 취소하면 code 대신 error가 온다
        if (params.get('error')) {
            setError(`${SOCIAL_PROVIDER_LABEL[provider]} 로그인을 취소했어요.`);
            return;
        }
        const checked = takeSocialState(provider, params.get('state'));
        const code = params.get('code');
        if (!checked || !code) {
            setError('로그인 요청이 올바르지 않아요. 처음부터 다시 시도해 주세요.');
            return;
        }
        const redirect = safeRedirect(checked.redirect);

        socialLogin(provider, code)
            .then((data) => {
                if (data?.status === 'LOGIN') {
                    navigate(redirect, { replace: true });
                    return;
                }
                const state: SocialSignupLocationState = {
                    provider,
                    signupToken: data?.signupToken ?? '',
                    email: data?.email ?? '',
                    nickname: data?.nickname ?? '',
                    redirect,
                };
                navigate('/signup/social', { replace: true, state });
            })
            .catch((e) => setError(errorMessage(e)));
    }, [provider, params, navigate]);

    return (
        <AuthCard title={`${label} 로그인`}>
            {error ? (
                <>
                    <Notice tone="error">{error}</Notice>
                    <Link to="/login" replace className={buttonVariants({ variant: 'outline' })}>
                        로그인 화면으로
                    </Link>
                </>
            ) : (
                <div className="flex flex-col gap-3" aria-busy="true">
                    <p className="text-body text-neutral-500">{label} 계정을 확인하고 있어요…</p>
                    <Skeleton className="h-11" />
                </div>
            )}
        </AuthCard>
    );
}