import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/api';
import { MyInfoContent } from './MyInfoContent';

// 내 정보 조회·수정 응답을 테스트마다 정한다
const server = vi.hoisted(() => ({
  me: (): unknown => undefined,
  update: ((): unknown => undefined) as (body: { nickname?: string; bio?: string }) => unknown,
}));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return {
    ...original,
    api: {
      GET: () => ({ kind: 'me' }),
      PATCH: (_path: string, init: { body: { nickname?: string; bio?: string } }) => ({ kind: 'update', body: init.body }),
    },
    call: async (request: { kind: string; body?: { nickname?: string; bio?: string } }) =>
      request.kind === 'me' ? server.me() : server.update(request.body ?? {}),
    replaceSession: vi.fn(),
  };
});

const me = { userId: 1, email: 'user@test.com', nickname: '경매왕', phoneNumber: '01012345678', role: 'USER', createdAt: '2026-10-01T10:00:00' };

function renderContent() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <MyInfoContent />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('MyInfoContent 닉네임 변경', () => {
  beforeEach(() => {
    server.me = () => me;
    server.update = () => undefined;
  });

  it('바꿀 수 있으면 변경 버튼이 켜져 있고, 입력 안내에 30일 제한을 알린다', async () => {
    renderContent();

    await userEvent.click(await screen.findByRole('button', { name: '변경' }));

    expect(screen.getByText(/바꾸면 30일 동안 다시 바꿀 수 없어요/)).toBeInTheDocument();
  });

  it('최근에 바꿔서 아직 못 바꾸면 변경 버튼을 끄고 다시 바꿀 수 있는 시각을 보여준다', async () => {
    server.me = () => ({ ...me, nicknameChangeAvailableAt: '2026-11-26T15:00:00' });
    renderContent();

    expect(await screen.findByRole('button', { name: '변경' })).toBeDisabled();
    expect(screen.getByText(/부터 다시 바꿀 수 있어요/)).toHaveTextContent('11월 26일');
  });

  it('바꾸고 나면 성공 안내와 다음 변경 가능 시각을 보여준다', async () => {
    server.update = ({ nickname }) => ({
      user: { ...me, nickname, nicknameChangeAvailableAt: '2026-11-26T15:00:00' },
      accessToken: 'new-token',
      expiresIn: 1800,
    });
    renderContent();

    await userEvent.click(await screen.findByRole('button', { name: '변경' }));
    const input = screen.getByLabelText('새 닉네임');
    await userEvent.clear(input);
    await userEvent.type(input, '새닉네임');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByText('닉네임을 변경했어요.')).toBeInTheDocument();
    expect(screen.getByText('새닉네임')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '변경' })).toBeDisabled();
  });

  it('서버가 아직 못 바꾼다고 하면(USER_005) 입력 아래에 안내한다', async () => {
    server.update = () => {
      throw new ApiError('USER_005', '닉네임을 바꾼 뒤 일정 기간이 지나야 다시 바꿀 수 있습니다.', 429);
    };
    renderContent();

    await userEvent.click(await screen.findByRole('button', { name: '변경' }));
    await userEvent.type(screen.getByLabelText('새 닉네임'), '2');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByText('닉네임은 한 번 바꾸면 30일 동안 다시 바꿀 수 없어요.')).toBeInTheDocument();
  });
});

describe('MyInfoContent 소개·프로필 사진', () => {
  beforeEach(() => {
    server.me = () => me;
    server.update = () => undefined;
  });

  it('소개를 적어 저장하면 소개만 보내고, 저장한 소개를 보여준다', async () => {
    const sent: unknown[] = [];
    server.update = (body) => {
      sent.push(body);
      return { user: { ...me, bio: body.bio }, accessToken: 'new-token', expiresIn: 1800 };
    };
    renderContent();

    await userEvent.click(await screen.findByRole('button', { name: '작성' }));
    await userEvent.type(screen.getByLabelText('자기소개'), '  빈티지 카메라를 모아요 ');
    await userEvent.click(screen.getByRole('button', { name: '저장' }));

    expect(await screen.findByText('소개를 저장했어요.')).toBeInTheDocument();
    expect(screen.getByText('빈티지 카메라를 모아요')).toBeInTheDocument();
    expect(sent).toEqual([{ bio: '빈티지 카메라를 모아요' }]);
  });

  it('올릴 수 없는 형식의 사진을 고르면 서버에 보내지 않고 안내한다', async () => {
    renderContent();

    const input = await screen.findByLabelText('프로필 사진 파일');
    await userEvent.upload(input, new File(['<svg/>'], 'a.svg', { type: 'image/svg+xml' }), { applyAccept: false });

    expect(await screen.findByText('JPG, PNG, WEBP, GIF 이미지만 올릴 수 있어요.')).toBeInTheDocument();
  });

  it('이메일·휴대폰 번호 옆에 변경 버튼이 있다', async () => {
    renderContent();

    expect(await screen.findByRole('button', { name: '이메일 변경' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '휴대폰 번호 변경' })).toBeInTheDocument();
  });
});
