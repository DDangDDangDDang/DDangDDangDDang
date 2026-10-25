import { MessageCircle } from 'lucide-react';
import { useState } from 'react';
import { Button, Notice } from '@/components/ui';
import { kakaoAuthorizeUrl, kakaoConfig } from '@/lib/kakao-login';

/**
 * [카카오로 시작하기]. 카카오 로그인 페이지로 이동한다. 로그인·회원가입 화면에서 쓴다.
 * 처음 온 사용자는 콜백 뒤 추가 정보 화면에서 가입하므로, 로그인과 가입이 같은 버튼이다.
 *
 * @param redirect 로그인(가입)을 마친 뒤 돌아갈 주소
 */
export function KakaoLoginButton({ redirect }: { redirect: string }) {
    const [notConfigured, setNotConfigured] = useState(false);

    function handleClick() {
        const config = kakaoConfig();
        if (!config) {
            setNotConfigured(true);
            return;
        }
        window.location.assign(kakaoAuthorizeUrl(config, redirect));
    }

    return (
        <div className="flex flex-col gap-2">
            <Button variant="kakao" size="lg" onClick={handleClick}>
                <MessageCircle className="size-5 fill-current" aria-hidden="true" />
                카카오로 시작하기
            </Button>
            {notConfigured && <Notice tone="error">카카오 로그인 설정이 필요합니다.</Notice>}
        </div>
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