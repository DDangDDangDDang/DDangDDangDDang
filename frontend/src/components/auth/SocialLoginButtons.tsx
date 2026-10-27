import { MessageCircle } from 'lucide-react';
import { useState } from 'react';
import { Button, Notice } from '@/components/ui';
import { SOCIAL_PROVIDER_LABEL, socialAuthorizeUrl, socialConfig, type SocialProvider } from '@/lib/social-login';

/**
 * [카카오로 시작하기] [Google로 계속하기]. 제공자 로그인 페이지로 이동한다. 로그인·회원가입 화면에서 쓴다.
 * 처음 온 사용자는 콜백 뒤 추가 정보 화면에서 가입하므로, 로그인과 가입이 같은 버튼이다.
 *
 * @param redirect 로그인(가입)을 마친 뒤 돌아갈 주소
 */
export function SocialLoginButtons({ redirect }: { redirect: string }) {
    return (
        <div className="flex flex-col gap-2">
            <SocialLoginButton provider="kakao" redirect={redirect} />
            <SocialLoginButton provider="google" redirect={redirect} />
        </div>
    );
}

export function SocialLoginButton({ provider, redirect }: { provider: SocialProvider; redirect: string }) {
    const [notConfigured, setNotConfigured] = useState(false);

    function handleClick() {
        const config = socialConfig(provider);
        if (!config) {
            setNotConfigured(true);
            return;
        }
        window.location.assign(socialAuthorizeUrl(provider, config, redirect));
    }

    return (
        <div className="flex flex-col gap-2">
            {provider === 'kakao' ? (
                <Button variant="kakao" size="lg" onClick={handleClick}>
                    <MessageCircle className="size-5 fill-current" aria-hidden="true" />
                    카카오로 시작하기
                </Button>
            ) : (
                // 구글 브랜딩 가이드: 흰 바탕, 테두리, 공식 G 로고
                <Button variant="outline" size="lg" onClick={handleClick}>
                    <GoogleLogo />
                    Google로 계속하기
                </Button>
            )}
            {notConfigured && <Notice tone="error">{SOCIAL_PROVIDER_LABEL[provider]} 로그인 설정이 필요합니다.</Notice>}
        </div>
    );
}

/** 구글 공식 "G" 로고. 구글 브랜딩 가이드가 정한 색이라 디자인 토큰이 아닌 원래 색을 쓴다. */
function GoogleLogo() {
    return (
        <svg className="size-5" viewBox="0 0 48 48" aria-hidden="true">
            <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
            <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
            <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
            <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
        </svg>
    );
}

/** 이메일 로그인과 소셜 로그인 사이 구분선 */
export function OrDivider() {
    return (
        <div className="flex items-center gap-3 text-caption text-neutral-500">
            <span className="h-px flex-1 bg-neutral-100" aria-hidden="true" />
            또는
            <span className="h-px flex-1 bg-neutral-100" aria-hidden="true" />
        </div>
    );
}