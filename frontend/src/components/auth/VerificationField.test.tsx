import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { VerificationField } from './VerificationField';

describe('VerificationField', () => {
  it('인증번호를 받으면 5분 타이머가 보이고, [인증번호 받기]는 1분 동안 비활성화된다', async () => {
    const user = userEvent.setup();
    const sendCode = vi.fn().mockResolvedValue(300);
    render(<VerificationField kind="phone" sendCode={sendCode} verify={vi.fn()} verified={false} />);

    await user.type(screen.getByLabelText('휴대폰 번호'), '010-1234-5678');
    expect(screen.getByLabelText('휴대폰 번호')).toHaveValue('01012345678'); // 하이픈은 지운다

    await user.click(screen.getByRole('button', { name: '인증번호 받기' }));

    expect(sendCode).toHaveBeenCalledWith('01012345678');
    // 응답이 화면에 반영될 때까지 기다린다. 그 사이 1초가 지날 수 있어 범위로 확인한다.
    expect(await screen.findByText(/^0(5:00|4:5\d)$/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /다시 받기 0(1:00|0:5\d)/ })).toBeDisabled();
  });

  it('6자리를 입력해야 [확인]을 누를 수 있고, 실패하면 서버 안내 문구를 보여준다', async () => {
    const user = userEvent.setup();
    const verify = vi.fn().mockRejectedValue(new ApiError('VERIFY_001', '인증번호가 올바르지 않거나 만료되었습니다.', 400));
    render(<VerificationField kind="phone" sendCode={vi.fn().mockResolvedValue(300)} verify={verify} verified={false} />);

    await user.type(screen.getByLabelText('휴대폰 번호'), '01012345678');
    await user.click(screen.getByRole('button', { name: '인증번호 받기' }));

    const confirm = await screen.findByRole('button', { name: '확인' });
    await user.type(screen.getByLabelText('인증번호'), '12345');
    expect(confirm).toBeDisabled();
    await user.type(screen.getByLabelText('인증번호'), '6');
    await user.click(confirm);

    expect(verify).toHaveBeenCalledWith('01012345678', '123456');
    expect(await screen.findByText('인증번호가 올바르지 않거나 만료되었습니다.')).toBeInTheDocument();
  });

  it('인증을 마치면 입력을 잠그고 완료 안내를 보여준다', () => {
    render(<VerificationField kind="phone" sendCode={vi.fn()} verify={vi.fn()} verified verifiedMessage="휴대폰 인증을 완료했어요." />);
    expect(screen.getByLabelText('휴대폰 번호')).toBeDisabled();
    expect(screen.getByText('휴대폰 인증을 완료했어요.')).toBeInTheDocument();
  });
});
