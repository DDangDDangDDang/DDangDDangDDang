import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { KakaoSignupPage, type KakaoSignupLocationState } from './KakaoSignupPage';

const kakaoSignup = vi.hoisted(() => vi.fn());
// 휴대폰 인증번호 발송 요청 (경로·본문 확인, 실패 응답은 테스트마다 정함)
const phoneCode = vi.hoisted(() => ({ requests: [] as { path: string; body?: unknown }[], fail: undefined as Error | undefined }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    kakaoSignup,
    api: { POST: (path: string, init?: { body?: unknown }) => ({ path, body: init?.body }) },
    call: async (r: { path: string; body?: unknown }) => {
      if (r.path.endsWith('/phone/code')) {
        phoneCode.requests.push(r);
        if (phoneCode.fail) throw phoneCode.fail;
        return { expiresIn: 300 };
      }
      if (r.path === '/api/v1/auth/signup/phone/verify') return { verificationToken: 'phone-token' };
      return undefined;
    },
  };
});

function Landed() {
  return <p>도착 {useLocation().pathname}</p>;
}

const pending: KakaoSignupLocationState = { signupToken: 'signup-token', email: 'u@kakao.com', nickname: '카카오닉', redirect: '/auctions/1' };

function renderPage(state: KakaoSignupLocationState | null = pending) {
  render(
    <MemoryRouter initialEntries={[{ pathname: '/signup/kakao', state }]}>
      <Routes>
        <Route path="/signup/kakao" element={<KakaoSignupPage />} />
        <Route path="*" element={<Landed />} />
      </Routes>
    </MemoryRouter>,
  );
}

async function verifyPhone() {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678');
  await user.click(screen.getByRole('button', { name: '인증번호 받기' }));
  await user.type(await screen.findByLabelText('인증번호'), '123456');
  await user.click(screen.getByRole('button', { name: '확인' }));
  expect(await screen.findByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
  return user;
}

describe('KakaoSignupPage', () => {
  beforeEach(() => {
    kakaoSignup.mockReset();
    phoneCode.requests = [];
    phoneCode.fail = undefined;
  });

  it('인증번호는 이미 가입된 번호도 받을 수 있는 카카오 가입용 API로 가입 토큰과 함께 요청한다', async () => {
    renderPage();
    await verifyPhone();

    expect(phoneCode.requests).toEqual([
      { path: '/api/v1/auth/oauth/kakao/phone/code', body: { signupToken: 'signup-token', phoneNumber: '01012345678' } },
    ]);
  });

  it('인증번호 요청 때 가입 시간이 지났으면(OAUTH_003) 카카오 로그인부터 다시 하게 한다', async () => {
    phoneCode.fail = new ApiError('OAUTH_003', '가입 시간이 지났습니다. 카카오 로그인부터 다시 진행해 주세요.', 400);
    renderPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678');
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }));

    expect(await screen.findByText('가입 시간이 지났습니다. 카카오 로그인부터 다시 진행해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /카카오로 시작하기/ })).toBeInTheDocument();
  });

  it('카카오 닉네임·이메일을 미리 채우고, 휴대폰 인증 전에는 가입할 수 없다', () => {
    renderPage();

    expect(screen.getByLabelText('닉네임')).toHaveValue('카카오닉');
    expect(screen.getByLabelText('이메일')).toHaveValue('u@kakao.com');
    expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();
  });

  it('가입하면 인증한 번호와 토큰을 보내고 원래 보던 화면으로 간다', async () => {
    kakaoSignup.mockResolvedValue({ linkedExistingAccount: false, accessToken: 'a', user: { userId: 1, nickname: '카카오닉' } });
    renderPage();
    const user = await verifyPhone();

    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('도착 /auctions/1')).toBeInTheDocument();
    expect(kakaoSignup).toHaveBeenCalledWith({
      signupToken: 'signup-token',
      nickname: '카카오닉',
      email: 'u@kakao.com',
      phoneNumber: '01012345678',
      phoneVerificationToken: 'phone-token',
    });
  });

  it('기존 계정에 연결됐으면 안내하고 [계속하기]로 이동한다', async () => {
    kakaoSignup.mockResolvedValue({ linkedExistingAccount: true, accessToken: 'a', user: { userId: 3, nickname: '기존회원' } });
    renderPage();
    const user = await verifyPhone();

    await user.click(screen.getByRole('button', { name: '가입하기' }));
    expect(await screen.findByText(/기존회원/)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '계속하기' }));
    expect(await screen.findByText('도착 /auctions/1')).toBeInTheDocument();
  });

  it.each([
    ['VERIFY_004', '인증이 만료되었습니다. 다시 인증해 주세요.'],
    ['OAUTH_004', '이 휴대폰 번호의 계정에는 이미 다른 카카오 계정이 연결되어 있습니다.'],
  ])('%s면 안내하고 휴대폰 인증을 다시 연다', async (code, message) => {
    kakaoSignup.mockRejectedValue(new ApiError(code, message, 400));
    renderPage();
    const user = await verifyPhone();

    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByLabelText('휴대폰 번호')).toBeEnabled();
    expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();
  });

  it('닉네임이 중복이면 안내만 하고 인증은 그대로 둔다', async () => {
    kakaoSignup.mockRejectedValue(new ApiError('USER_002', '이미 사용 중인 닉네임입니다.', 409));
    renderPage();
    const user = await verifyPhone();

    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('이미 사용 중인 닉네임입니다.')).toBeInTheDocument();
    expect(screen.getByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
  });

  it('가입 시간이 지나면(OAUTH_003) 카카오 로그인부터 다시 하게 한다', async () => {
    kakaoSignup.mockRejectedValue(new ApiError('OAUTH_003', '가입 시간이 지났습니다. 카카오 로그인부터 다시 진행해 주세요.', 400));
    renderPage();
    const user = await verifyPhone();

    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('가입 시간이 지났습니다. 카카오 로그인부터 다시 진행해 주세요.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /카카오로 시작하기/ })).toBeInTheDocument();
    expect(screen.queryByLabelText('닉네임')).not.toBeInTheDocument();
  });

  it('새로고침 등으로 가입 정보가 없으면 로그인부터 다시 하게 한다', () => {
    renderPage(null);

    expect(screen.getByText('가입 정보가 없어요. 카카오 로그인부터 다시 진행해 주세요.')).toBeInTheDocument();
    expect(screen.queryByLabelText('닉네임')).not.toBeInTheDocument();
  });
});
