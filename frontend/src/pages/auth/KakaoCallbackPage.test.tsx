import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { kakaoAuthorizeUrl } from '@/lib/kakao-login';
import { KakaoCallbackPage } from './KakaoCallbackPage';

const kakaoLogin = vi.hoisted(() => vi.fn());
vi.mock('@/api', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/api')>()), kakaoLogin }));

/** 이동한 화면의 경로와 넘겨받은 state를 보여준다 */
function Landed() {
  const location = useLocation();
  return <p>도착 {location.pathname} {JSON.stringify(location.state)}</p>;
}

function renderCallback(search: string) {
  render(
    <MemoryRouter initialEntries={[`/oauth/kakao/callback${search}`]}>
      <Routes>
        <Route path="/oauth/kakao/callback" element={<KakaoCallbackPage />} />
        <Route path="*" element={<Landed />} />
      </Routes>
    </MemoryRouter>,
  );
}

/** 카카오 로그인을 시작한 것처럼 state를 저장하고 그 값을 돌려준다 */
function startLogin(redirect = '/auctions/1') {
  const url = kakaoAuthorizeUrl({ restApiKey: 'key', redirectUri: 'http://localhost/oauth/kakao/callback' }, redirect);
  return new URL(url).searchParams.get('state')!;
}

describe('KakaoCallbackPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    kakaoLogin.mockReset();
  });

  it('연결된 회원이면 로그인하고 시작할 때 보던 화면으로 돌아간다', async () => {
    kakaoLogin.mockResolvedValue({ status: 'LOGIN', accessToken: 'a', user: { userId: 1 } });
    const state = startLogin('/auctions/1');

    renderCallback(`?code=auth-code&state=${state}`);

    expect(await screen.findByText(/도착 \/auctions\/1/)).toBeInTheDocument();
    expect(kakaoLogin).toHaveBeenCalledWith('auth-code');
  });

  it('처음 온 사용자면 가입 토큰과 카카오 이메일·닉네임을 들고 추가 정보 화면으로 간다', async () => {
    kakaoLogin.mockResolvedValue({ status: 'SIGNUP_REQUIRED', signupToken: 'signup-token', email: 'u@kakao.com', nickname: '카카오닉' });
    const state = startLogin('/');

    renderCallback(`?code=auth-code&state=${state}`);

    const landed = await screen.findByText(/도착 \/signup\/kakao/);
    expect(landed.textContent).toContain('"signupToken":"signup-token"');
    expect(landed.textContent).toContain('"email":"u@kakao.com"');
    expect(landed.textContent).toContain('"nickname":"카카오닉"');
  });

  it('state가 다르면 인가 코드를 보내지 않고 거절한다', async () => {
    startLogin();

    renderCallback('?code=auth-code&state=forged');

    expect(await screen.findByText('로그인 요청이 올바르지 않아요. 처음부터 다시 시도해 주세요.')).toBeInTheDocument();
    expect(kakaoLogin).not.toHaveBeenCalled();
  });

  it('카카오 동의 화면에서 취소하면 안내한다', async () => {
    renderCallback('?error=access_denied&state=x');

    expect(await screen.findByText('카카오 로그인을 취소했어요.')).toBeInTheDocument();
    expect(kakaoLogin).not.toHaveBeenCalled();
  });

  it('서버가 거절하면(인가 코드 만료 등) 오류를 보여준다', async () => {
    kakaoLogin.mockRejectedValue(new ApiError('OAUTH_001', '카카오 로그인에 실패했습니다. 다시 시도해 주세요.', 400));
    const state = startLogin();

    renderCallback(`?code=used-code&state=${state}`);

    expect(await screen.findByText('카카오 로그인에 실패했습니다. 다시 시도해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '로그인 화면으로' })).toBeInTheDocument();
  });
});
