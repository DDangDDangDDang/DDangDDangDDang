import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SellerIdentityStep } from './SellerIdentityStep';

type Request = { method: string; path: string; body?: unknown };

const server = vi.hoisted(() => ({ requests: [] as Request[] }));
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  const request = (method: string) => (path: string, init?: { body?: unknown }) => ({ method, path, body: init?.body });
  return {
    ...original,
    api: { GET: request('GET'), POST: request('POST') },
    call: async (r: Request) => {
      server.requests.push(r);
      return { realName: '홍길동', birthDate: '1990-01-31' };
    },
  };
});
const portOne = vi.hoisted(() => ({ requestIdentityVerification: vi.fn() }));
vi.mock('@portone/browser-sdk/v2', () => portOne);

function renderStep() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <SellerIdentityStep />
    </QueryClientProvider>,
  );
}

describe('SellerIdentityStep', () => {
  beforeEach(() => {
    server.requests = [];
    portOne.requestIdentityVerification.mockReset();
    vi.stubEnv('VITE_PORTONE_STORE_ID', 'store-test');
    vi.stubEnv('VITE_PORTONE_IDENTITY_CHANNEL_KEY', 'channel-key-identity');
  });
  afterEach(() => vi.unstubAllEnvs());

  it('본인인증 창을 마치면 인증 ID를 서버로 보낸다', async () => {
    portOne.requestIdentityVerification.mockResolvedValue({ identityVerificationId: 'identity-1' });
    renderStep();

    await userEvent.click(screen.getByRole('button', { name: '휴대폰 본인인증' }));

    const sent = await vi.waitFor(() => {
      const found = server.requests.find((r) => r.path === '/api/v1/seller-applications/identity');
      if (!found) throw new Error('not yet');
      return found;
    });
    const [windowRequest] = portOne.requestIdentityVerification.mock.lastCall ?? [];
    expect(sent.body).toEqual({ identityVerificationId: windowRequest?.identityVerificationId });
    expect(windowRequest?.identityVerificationId).toMatch(/^identity-/);
  });

  it('창을 열지 못하면(팝업 차단 등) 알 수 없는 오류 대신 팝업 차단 해제를 안내하고 서버로 보내지 않는다', async () => {
    portOne.requestIdentityVerification.mockRejectedValue(new Error('popup blocked'));
    renderStep();

    await userEvent.click(screen.getByRole('button', { name: '휴대폰 본인인증' }));

    expect(await screen.findByText(/팝업 차단을 해제한 뒤 다시 시도해 주세요/)).toBeInTheDocument();
    expect(server.requests).toEqual([]);
  });

  it('창을 닫거나 인증이 실패하면 포트원 메시지를 보여주고 서버로 보내지 않는다', async () => {
    portOne.requestIdentityVerification.mockResolvedValue({ code: 'FAILURE_TYPE_PG', message: '사용자가 인증을 취소했습니다.' });
    renderStep();

    await userEvent.click(screen.getByRole('button', { name: '휴대폰 본인인증' }));

    expect(await screen.findByText('사용자가 인증을 취소했습니다.')).toBeInTheDocument();
    expect(server.requests).toEqual([]);
  });
});
