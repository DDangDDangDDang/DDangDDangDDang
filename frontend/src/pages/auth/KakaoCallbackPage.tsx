import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { errorMessage, kakaoLogin } from '@/api';
import { AuthCard } from '@/components/layout/PageHeader';
import { Notice, Skeleton, buttonVariants } from '@/components/ui';
import { takeKakaoState } from '@/lib/kakao-login';
import { safeRedirect } from '@/lib/redirect';
import type { KakaoSignupLocationState } from './KakaoSignupPage';

/**
 * 카카오 로그인 콜백 (/oauth/kakao/callback?code=...&state=...)
 * - state가 시작할 때 저장한 값과 다르면 거절한다. (다른 사이트가 만든 로그인 요청 차단)
 * - 연결된 회원이면 로그인하고 원래 보던 화면으로, 처음이면 추가 정보 화면으로 보낸다.
 */
export function KakaoCallbackPage() {
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const [error, setError] = useState<string>();
    // 개발 모드(StrictMode)에서 effect가 두 번 돌아도 인가 코드는 한 번만 보낸다. (카카오 인가 코드는 1회용)
    const started = useRef(false);

    useEffect(() => {
        if (started.current) return;
        started.current = true;

        // 사용자가 카카오 동의 화면에서 취소하면 code 대신 error가 온다
        if (params.get('error')) {
            setError('카카오 로그인을 취소했어요.');
            return;
        }
        const checked = takeKakaoState(params.get('state'));
        const code = params.get('code');
        if (!checked || !code) {
            setError('로그인 요청이 올바르지 않아요. 처음부터 다시 시도해 주세요.');
            return;
        }
        const redirect = safeRedirect(checked.redirect);

        kakaoLogin(code)
            .then((data) => {
                if (data?.status === 'LOGIN') {
                    navigate(redirect, { replace: true });
                    return;
                }
                const state: KakaoSignupLocationState = {
                    signupToken: data?.signupToken ?? '',
                    email: data?.email ?? '',
                    nickname: data?.nickname ?? '',
                    redirect,
                };
                navigate('/signup/kakao', { replace: true, state });
            })
            .catch((e) => setError(errorMessage(e)));
    }, [params, navigate]);

    return (
        <AuthCard title="카카오 로그인">
            {error ? (
                <>
                    <Notice tone="error">{error}</Notice>
                    <Link to="/login" replace className={buttonVariants({ variant: 'outline' })}>
                        로그인 화면으로
                    </Link>
                </>
            ) : (
                <div className="flex flex-col gap-3" aria-busy="true">
                    <p className="text-body text-neutral-500">카카오 계정을 확인하고 있어요…</p>
                    <Skeleton className="h-11" />
                </div>
            )}
        </AuthCard>
    );
}