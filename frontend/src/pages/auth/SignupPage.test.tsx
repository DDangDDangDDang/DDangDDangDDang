import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { SignupPage } from './SignupPage';

type Request = { path: string; body?: unknown };

// api.POST는 요청 내용만 돌려주고, call()이 경로별로 응답한다. 가입 요청의 결과는 테스트마다 정한다.
const server = vi.hoisted(() => ({ signup: (): unknown => undefined }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => null,
    login: vi.fn(),
    api: { POST: (path: string, init?: { body?: unknown }) => ({ path, body: init?.body }) },
    call: async (r: Request) => {
      if (r.path === '/api/v1/auth/signup/phone/code') return { expiresIn: 300 };
      if (r.path === '/api/v1/auth/signup/phone/verify') return { verificationToken: 'phone-token' };
      if (r.path === '/api/v1/auth/signup') return server.signup();
      return undefined;
    },
  };
});

async function fillAndVerify() {
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <SignupPage />
    </MemoryRouter>,
  );
  await user.type(screen.getByLabelText('이메일'), 'new@test.com');
  await user.type(screen.getByLabelText('비밀번호'), 'Abcd1234!');
  await user.type(screen.getByLabelText('닉네임'), '새회원');
  await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678');
  await user.click(screen.getByRole('button', { name: '인증번호 받기' }));
  await user.type(await screen.findByLabelText('인증번호'), '123456');
  await user.click(screen.getByRole('button', { name: '확인' }));
  expect(await screen.findByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
  expect(screen.getByLabelText('휴대폰 번호')).toBeDisabled();
  return user;
}

describe('SignupPage', () => {
  beforeEach(() => {
    server.signup = () => undefined;
  });
  afterEach(() => vi.restoreAllMocks());

  it.each([
    ['USER_004', '이미 가입된 휴대폰 번호입니다.'],
    ['VERIFY_004', '인증이 만료되었습니다. 다시 인증해 주세요.'],
  ])('가입이 %s로 실패하면 안내를 보여주고 휴대폰 인증을 다시 할 수 있게 연다', async (code, message) => {
    server.signup = () => {
      throw new ApiError(code, message, 409);
    };
    const user = await fillAndVerify();
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.getByLabelText('휴대폰 번호')).toBeEnabled();
    expect(screen.queryByText('휴대폰 인증을 완료했어요.')).not.toBeInTheDocument();
    // 이미 쓴 인증번호 입력은 지우고 [인증번호 받기]부터 다시 하게 한다.
    expect(screen.queryByLabelText('인증번호')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();

    // 다른 번호로 다시 인증하면 이전 가입 실패 안내가 사라진다.
    vi.spyOn(Date, 'now').mockReturnValue(Date.now() + 61_000);
    await user.clear(screen.getByLabelText('휴대폰 번호'));
    await user.type(screen.getByLabelText('휴대폰 번호'), '01087654321');
    await user.click(await screen.findByRole('button', { name: '인증번호 받기' }, { timeout: 3000 }));
    await user.type(await screen.findByLabelText('인증번호'), '654321');
    await user.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
    expect(screen.queryByText(message)).not.toBeInTheDocument();
    // 재요청 대기 시계가 1초마다 바뀌는 것을 기다리므로 기본 제한(5초)보다 넉넉히 둔다.
  }, 15_000);

  it('이메일 중복(USER_001)이면 휴대폰 인증은 그대로 두어 이메일만 고쳐 다시 가입할 수 있다', async () => {
    server.signup = () => {
      throw new ApiError('USER_001', '이미 사용 중인 이메일입니다.', 409);
    };
    const user = await fillAndVerify();
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('이미 사용 중인 이메일입니다.')).toBeInTheDocument();
    expect(screen.getByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
    expect(screen.getByLabelText('휴대폰 번호')).toBeDisabled();
    expect(screen.getByRole('button', { name: '가입하기' })).toBeEnabled();
  });
});
