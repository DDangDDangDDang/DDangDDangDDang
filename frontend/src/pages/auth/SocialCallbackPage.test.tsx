import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { socialAuthorizeUrl } from '@/lib/social-login';
import { SocialCallbackPage } from './SocialCallbackPage';

const socialLogin = vi.hoisted(() => vi.fn());
vi.mock('@/api', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/api')>()), socialLogin }));

/** 이동한 화면의 경로와 넘겨받은 state를 보여준다 */
function Landed() {
  const location = useLocation();
  return <p>도착 {location.pathname} {JSON.stringify(location.state)}</p>;
}

function renderCallback(search: string, provider = 'kakao') {
  render(
    <MemoryRouter initialEntries={[`/oauth/${provider}/callback${search}`]}>
      <Routes>
        <Route path="/oauth/:provider/callback" element={<SocialCallbackPage />} />
        <Route path="*" element={<Landed />} />
      </Routes>
    </MemoryRouter>,
  );
}

/** 카카오 로그인을 시작한 것처럼 state를 저장하고 그 값을 돌려준다 */
function startLogin(redirect = '/auctions/1', provider: 'kakao' | 'google' = 'kakao') {
  const url = socialAuthorizeUrl(provider, { clientId: 'key', redirectUri: `http://localhost/oauth/${provider}/callback` }, redirect);
  return new URL(url).searchParams.get('state')!;
}

describe('SocialCallbackPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    socialLogin.mockReset();
  });

  it('연결된 회원이면 로그인하고 시작할 때 보던 화면으로 돌아간다', async () => {
    socialLogin.mockResolvedValue({ status: 'LOGIN', accessToken: 'a', user: { userId: 1 } });
    const state = startLogin('/auctions/1');

    renderCallback(`?code=auth-code&state=${state}`);

    expect(await screen.findByText(/도착 \/auctions\/1/)).toBeInTheDocument();
    expect(socialLogin).toHaveBeenCalledWith('kakao', 'auth-code');
  });

  it('처음 온 사용자면 가입 토큰과 카카오 이메일·닉네임을 들고 추가 정보 화면으로 간다', async () => {
    socialLogin.mockResolvedValue({ status: 'SIGNUP_REQUIRED', signupToken: 'signup-token', email: 'u@kakao.com', nickname: '카카오닉' });
    const state = startLogin('/');

    renderCallback(`?code=auth-code&state=${state}`);

    const landed = await screen.findByText(/도착 \/signup\/social/);
    expect(landed.textContent).toContain('"provider":"kakao"');
    expect(landed.textContent).toContain('"signupToken":"signup-token"');
    expect(landed.textContent).toContain('"email":"u@kakao.com"');
    expect(landed.textContent).toContain('"nickname":"카카오닉"');
  });

  it('state가 다르면 인가 코드를 보내지 않고 거절한다', async () => {
    startLogin();

    renderCallback('?code=auth-code&state=forged');

    expect(await screen.findByText('로그인 요청이 올바르지 않아요. 처음부터 다시 시도해 주세요.')).toBeInTheDocument();
    expect(socialLogin).not.toHaveBeenCalled();
  });

  it('구글 콜백이면 구글 제공자로 인가 코드를 보낸다', async () => {
    socialLogin.mockResolvedValue({ status: 'LOGIN', accessToken: 'a', user: { userId: 1 } });
    const state = startLogin('/', 'google');

    renderCallback(`?code=google-code&state=${state}`, 'google');

    expect(await screen.findByText(/^도착 \/ /)).toBeInTheDocument();
    expect(socialLogin).toHaveBeenCalledWith('google', 'google-code');
  });

  it('카카오로 시작한 state를 구글 콜백에 쓰면 거절한다', async () => {
    const state = startLogin('/', 'kakao');

    renderCallback(`?code=code&state=${state}`, 'google');

    expect(await screen.findByText('로그인 요청이 올바르지 않아요. 처음부터 다시 시도해 주세요.')).toBeInTheDocument();
    expect(socialLogin).not.toHaveBeenCalled();
  });

  it('지원하지 않는 제공자 주소면 안내한다', async () => {
    renderCallback('?code=code&state=x', 'naver');

    expect(await screen.findByText('지원하지 않는 로그인 방식이에요.')).toBeInTheDocument();
    expect(socialLogin).not.toHaveBeenCalled();
  });

  it('카카오 동의 화면에서 취소하면 안내한다', async () => {
    renderCallback('?error=access_denied&state=x');

    expect(await screen.findByText('카카오 로그인을 취소했어요.')).toBeInTheDocument();
    expect(socialLogin).not.toHaveBeenCalled();
  });

  it('서버가 거절하면(인가 코드 만료 등) 오류를 보여준다', async () => {
    socialLogin.mockRejectedValue(new ApiError('OAUTH_001', '카카오 로그인에 실패했습니다. 다시 시도해 주세요.', 400));
    const state = startLogin();

    renderCallback(`?code=used-code&state=${state}`);

    expect(await screen.findByText('카카오 로그인에 실패했습니다. 다시 시도해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인 화면으로' })).toBeInTheDocument();
  });
});
