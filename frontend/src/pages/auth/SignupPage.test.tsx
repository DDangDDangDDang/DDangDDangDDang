import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { SignupPage } from './SignupPage';

type Request = { path: string; body?: unknown };

// api.POST는 요청 내용만 돌려주고, call()이 경로별로 응답한다. 가입 요청의 결과는 테스트마다 정한다.
const server = vi.hoisted(() => ({ signup: ((): unknown => undefined) as (request?: { path: string; body?: unknown }) => unknown }));
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
      if (r.path === '/api/v1/auth/signup/email/code') return { expiresIn: 300 };
      if (r.path === '/api/v1/auth/signup/email/verify') return { verificationToken: 'email-token' };
      if (r.path === '/api/v1/auth/signup') return server.signup(r);
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
  await user.type(screen.getByLabelText('비밀번호'), 'Abcd1234!');
  await user.type(screen.getByLabelText('닉네임'), '새회원');

  // 이메일 인증
  const emailGroup = screen.getByRole('group', { name: '이메일 인증' });
  await user.type(within(emailGroup).getByLabelText('이메일'), 'new@test.com');
  await user.click(within(emailGroup).getByRole('button', { name: '인증번호 받기' }));
  await user.type(await within(emailGroup).findByLabelText('인증번호'), '111111');
  await user.click(within(emailGroup).getByRole('button', { name: '확인' }));
  expect(await screen.findByText('이메일 인증을 완료했어요.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();

  // 휴대폰 인증
  const phoneGroup = screen.getByRole('group', { name: '휴대폰 인증' });
  await user.type(within(phoneGroup).getByLabelText('휴대폰 번호'), '01012345678');
  await user.click(within(phoneGroup).getByRole('button', { name: '인증번호 받기' }));
  await user.type(await within(phoneGroup).findByLabelText('인증번호'), '123456');
  await user.click(within(phoneGroup).getByRole('button', { name: '확인' }));
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
    const phoneGroup = screen.getByRole('group', { name: '휴대폰 인증' });
    await user.clear(within(phoneGroup).getByLabelText('휴대폰 번호'));
    await user.type(within(phoneGroup).getByLabelText('휴대폰 번호'), '01087654321');
    await user.click(await within(phoneGroup).findByRole('button', { name: '인증번호 받기' }, { timeout: 3000 }));
    await user.type(await within(phoneGroup).findByLabelText('인증번호'), '654321');
    await user.click(within(phoneGroup).getByRole('button', { name: '확인' }));
    expect(await screen.findByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
    expect(screen.queryByText(message)).not.toBeInTheDocument();
    // 재요청 대기 시계가 1초마다 바뀌는 것을 기다리므로 기본 제한(5초)보다 넉넉히 둔다.
  }, 15_000);

  it('이메일 중복(USER_001)이면 휴대폰 인증은 그대로 두고 이메일 인증만 다시 연다', async () => {
    server.signup = () => {
      throw new ApiError('USER_001', '이미 사용 중인 이메일입니다.', 409);
    };
    const user = await fillAndVerify();
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    expect(await screen.findByText('이미 사용 중인 이메일입니다.')).toBeInTheDocument();
    expect(screen.getByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
    expect(screen.getByLabelText('휴대폰 번호')).toBeDisabled();
    expect(screen.queryByText('이메일 인증을 완료했어요.')).not.toBeInTheDocument();
    expect(screen.getByLabelText('이메일')).toBeEnabled();
    expect(screen.getByRole('button', { name: '가입하기' })).toBeDisabled();
  });

  it('인증한 이메일과 두 인증 토큰을 함께 보낸다', async () => {
    let body: unknown;
    server.signup = (r?: Request) => {
      body = r?.body;
      return undefined;
    };
    const user = await fillAndVerify();
    await user.click(screen.getByRole('button', { name: '가입하기' }));

    await vi.waitFor(() => expect(body).toBeDefined());
    expect(body).toEqual({
      email: 'new@test.com',
      password: 'Abcd1234!',
      nickname: '새회원',
      phoneNumber: '01012345678',
      phoneVerificationToken: 'phone-token',
      emailVerificationToken: 'email-token',
    });
  });
});
