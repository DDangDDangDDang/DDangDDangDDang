import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { PasswordResetPage } from './PasswordResetPage';

type Request = { path: string; body?: unknown };

vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    useSession: () => null,
    clearSession: vi.fn(),
    api: { POST: (path: string, init?: { body?: unknown }) => ({ path, body: init?.body }) },
    call: async (r: Request) => {
      if (r.path === '/api/v1/auth/password-reset/code') return { expiresIn: 300 };
      if (r.path === '/api/v1/auth/password-reset/verify') return { verificationToken: 'reset-token' };
      // 인증 후 10분이 지난 상황
      if (r.path === '/api/v1/auth/password-reset') throw new ApiError('VERIFY_004', '인증이 만료되었습니다. 다시 인증해 주세요.', 400);
      return undefined;
    },
  };
});

describe('PasswordResetPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('인증이 만료돼(VERIFY_004) 변경에 실패하면 안내를 보여주고 이메일 인증을 다시 할 수 있게 연다', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <PasswordResetPage />
      </MemoryRouter>,
    );
    await user.type(screen.getByLabelText('이메일'), 'me@test.com');
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }));
    await user.type(await screen.findByLabelText('인증번호'), '123456');
    await user.click(screen.getByRole('button', { name: '확인' }));
    expect(screen.getByLabelText('이메일')).toBeDisabled();

    await user.type(await screen.findByLabelText('새 비밀번호'), 'Newpass1!');
    await user.type(screen.getByLabelText('새 비밀번호 확인'), 'Newpass1!');
    await user.click(screen.getByRole('button', { name: '변경' }));

    expect(await screen.findByText('인증이 만료됐어요. 다시 인증해 주세요.')).toBeInTheDocument();
    expect(screen.getByLabelText('이메일')).toBeEnabled();
    expect(screen.queryByLabelText('새 비밀번호')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('인증번호')).not.toBeInTheDocument();

    // 같은 이메일로 다시 인증하면(재요청 대기 1분이 지난 뒤) 만료 안내가 사라진다.
    const later = Date.now() + 61_000;
    vi.spyOn(Date, 'now').mockReturnValue(later);
    await user.click(await screen.findByRole('button', { name: '인증번호 받기' }, { timeout: 3000 }));
    await user.type(await screen.findByLabelText('인증번호'), '654321');
    await user.click(screen.getByRole('button', { name: '확인' }));
    expect(await screen.findByLabelText('새 비밀번호')).toBeInTheDocument();
    expect(screen.queryByText('인증이 만료됐어요. 다시 인증해 주세요.')).not.toBeInTheDocument();
    // 재요청 대기 시계가 1초마다 바뀌는 것을 기다리므로 기본 제한(5초)보다 넉넉히 둔다.
  }, 15_000);
});
