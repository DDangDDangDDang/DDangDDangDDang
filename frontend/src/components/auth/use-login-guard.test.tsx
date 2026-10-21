import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useLoginGuard } from './use-login-guard';

const api = vi.hoisted(() => ({ getSession: vi.fn(), login: vi.fn() }));
vi.mock('@/api', async (importOriginal) => ({ ...(await importOriginal<typeof import('@/api')>()), getSession: api.getSession, login: api.login }));

function Chat({ onSend }: { onSend: () => void }) {
  const { guard, loginDialog } = useLoginGuard();
  return (
    <>
      <button type="button" onClick={() => guard(onSend)}>
        채팅 보내기
      </button>
      {loginDialog}
    </>
  );
}

function renderAt(onSend: () => void) {
  return render(
    <MemoryRouter initialEntries={['/lives/7']}>
      <Routes>
        <Route path="/lives/:id" element={<Chat onSend={onSend} />} />
        <Route path="/signup" element={<p>회원가입 화면</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('useLoginGuard', () => {
  beforeEach(() => {
    api.getSession.mockReset();
    api.login.mockReset();
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('로그인했으면 창 없이 바로 한다', async () => {
    api.getSession.mockReturnValue({ accessToken: 't', user: { userId: 1 } });
    const onSend = vi.fn();
    renderAt(onSend);

    await userEvent.click(screen.getByRole('button', { name: '채팅 보내기' }));
    expect(onSend).toHaveBeenCalledOnce();
  });

  it('로그인하지 않았으면 "로그인이 필요합니다" 창을 띄우고, 창에서 로그인하면 하던 동작을 이어서 한다', async () => {
    api.getSession.mockReturnValue(null);
    api.login.mockResolvedValue(undefined);
    const onSend = vi.fn();
    renderAt(onSend);

    await userEvent.click(screen.getByRole('button', { name: '채팅 보내기' }));
    expect(screen.getByText('로그인이 필요합니다. 로그인 하시겠습니까?')).toBeInTheDocument();
    expect(onSend).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: '로그인' }));
    await userEvent.type(screen.getByLabelText('이메일'), 'viewer@test.com');
    await userEvent.type(screen.getByLabelText('비밀번호'), 'Abcd1234!');
    await userEvent.click(screen.getByRole('button', { name: '로그인' }));

    expect(api.login).toHaveBeenCalledWith({ email: 'viewer@test.com', password: 'Abcd1234!' });
    await vi.waitFor(() => expect(onSend).toHaveBeenCalledOnce());
  });

  it('[회원가입]을 누르면 회원가입 화면으로 가고, 가입 뒤 돌아올 주소를 넘긴다', async () => {
    api.getSession.mockReturnValue(null);
    renderAt(vi.fn());

    await userEvent.click(screen.getByRole('button', { name: '채팅 보내기' }));
    await userEvent.click(screen.getByRole('button', { name: '회원가입' }));
    expect(screen.getByText('회원가입 화면')).toBeInTheDocument();
  });
});
