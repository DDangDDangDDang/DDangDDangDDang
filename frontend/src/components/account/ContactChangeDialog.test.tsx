import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { ContactChangeDialog } from './ContactChangeDialog';
import type { MyInfo } from './my-info-query';

type Request = { method: string; path: string; body?: unknown };

// api.POST·PUT은 요청 내용만 돌려주고, call()이 경로별로 응답한다.
const server = vi.hoisted(() => ({
  requests: [] as { method: string; path: string; body?: unknown }[],
  change: (): unknown => ({ phoneNumber: '01099998888' }),
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    replaceSession: vi.fn(),
    api: {
      POST: (path: string, init?: { body?: unknown }) => ({ method: 'POST', path, body: init?.body }),
      PUT: (path: string, init?: { body?: unknown }) => ({ method: 'PUT', path, body: init?.body }),
    },
    call: async (r: Request) => {
      server.requests.push(r);
      if (r.path === '/api/v1/users/me/reauth/password') {
        if ((r.body as { password: string }).password !== 'Abcd1234!') {
          throw new original.ApiError('USER_017', '비밀번호가 맞지 않습니다.', 400);
        }
        return { verificationToken: 'reauth-token' };
      }
      if (r.path === '/api/v1/users/me/reauth/code' || r.path.endsWith('/code')) return { expiresIn: 300 };
      if (r.path === '/api/v1/users/me/reauth/verify') return { verificationToken: 'reauth-token' };
      if (r.method === 'PUT') return server.change();
      return undefined;
    },
  };
});

const withPassword: MyInfo = { userId: 1, email: 'user@test.com', phoneNumber: '01012345678', hasPassword: true };
const socialOnly: MyInfo = { userId: 2, email: 'social@test.com', phoneNumber: '01055556666', hasPassword: false };

function renderDialog(me: MyInfo) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <ContactChangeDialog kind="phone" me={me} open onClose={() => undefined} />
      </MemoryRouter>
    </QueryClientProvider>,
  );
  return userEvent.setup();
}

async function changePhone(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('휴대폰 번호'), '01099998888');
  await user.click(screen.getByRole('button', { name: '인증번호 받기' }));
  await user.type(await screen.findByLabelText('인증번호'), '123456');
  await user.click(screen.getByRole('button', { name: '확인' }));
}

describe('ContactChangeDialog', () => {
  beforeEach(() => {
    server.requests = [];
    server.change = () => ({ phoneNumber: '01099998888' });
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('비밀번호로 본인 확인을 먼저 하고, 받은 토큰을 변경 요청에 넣는다', async () => {
    const user = renderDialog(withPassword);
    expect(screen.queryByLabelText('휴대폰 번호')).not.toBeInTheDocument();

    await user.type(screen.getByLabelText('지금 비밀번호'), 'Abcd1234!');
    await user.click(screen.getByRole('button', { name: '확인' }));
    await changePhone(user);

    expect(await screen.findByText('휴대폰 번호를 바꿨어요.')).toBeInTheDocument();
    expect(server.requests.find((r) => r.method === 'PUT')?.body).toEqual({
      phoneNumber: '01099998888',
      code: '123456',
      reauthToken: 'reauth-token',
    });
  });

  it('비밀번호가 틀리면 입력 아래에 안내하고 다음 단계로 가지 않는다', async () => {
    const user = renderDialog(withPassword);

    await user.type(screen.getByLabelText('지금 비밀번호'), 'Wrong1234!');
    await user.click(screen.getByRole('button', { name: '확인' }));

    expect(await screen.findByText('비밀번호가 맞지 않습니다.')).toBeInTheDocument();
    expect(screen.getByLabelText('지금 비밀번호')).toBeInTheDocument();
  });

  it('비밀번호가 없는 소셜 가입 회원은 지금 휴대폰 번호로 받은 인증번호로 본인 확인을 한다', async () => {
    const user = renderDialog(socialOnly);

    expect(screen.getByLabelText('휴대폰 번호')).toHaveValue('01055556666');
    expect(screen.getByLabelText('휴대폰 번호')).toBeDisabled();
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }));
    await user.type(await screen.findByLabelText('인증번호'), '654321');
    await user.click(screen.getByRole('button', { name: '확인' }));

    // 본인 확인이 끝나면 새 번호 입력으로 넘어간다
    expect(await screen.findByText(/새 번호로 받은 인증번호/)).toBeInTheDocument();
    expect(server.requests.map((r) => r.path)).toEqual(['/api/v1/users/me/reauth/code', '/api/v1/users/me/reauth/verify']);
  });

  it('비밀번호도 휴대폰 번호도 없으면 비밀번호 찾기로 안내한다', () => {
    renderDialog({ ...socialOnly, phoneNumber: undefined });

    expect(screen.getByText(/비밀번호 찾기로 비밀번호를 만든 뒤/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '비밀번호 찾기' })).toHaveAttribute('href', '/password-reset');
  });

  it('본인 확인이 만료됐으면(VERIFY_004) 본인 확인부터 다시 하게 한다', async () => {
    server.change = () => {
      throw new ApiError('VERIFY_004', '인증이 만료되었습니다. 다시 인증해 주세요.', 400);
    };
    const user = renderDialog(withPassword);
    await user.type(screen.getByLabelText('지금 비밀번호'), 'Abcd1234!');
    await user.click(screen.getByRole('button', { name: '확인' }));
    await changePhone(user);

    expect(await screen.findByText('본인 확인 시간이 지났어요. 다시 확인해 주세요.')).toBeInTheDocument();
    expect(screen.getByLabelText('지금 비밀번호')).toHaveValue('');
  });
});
