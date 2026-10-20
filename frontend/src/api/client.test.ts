import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api, call, refreshAccessToken } from './client';
import { ApiError, errorMessage } from './errors';
import { getAccessToken, setSession } from './session';

const user = { userId: 1, nickname: '경매왕초보', role: 'USER' as const };

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('API 클라이언트', () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    setSession(null);
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it('Access Token을 Authorization 헤더로 붙인다', async () => {
    setSession({ accessToken: 'token-1', user });
    fetchMock.mockResolvedValueOnce(json(200, { success: true, data: { id: 7 } }));

    const data = await call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId: 7 } } }));

    expect(data).toEqual({ id: 7 });
    const request = fetchMock.mock.calls[0]![0] as Request;
    expect(request.headers.get('Authorization')).toBe('Bearer token-1');
  });

  it('값이 빈 Authorization 헤더도 Access Token으로 채운다 (로그아웃 요청)', async () => {
    setSession({ accessToken: 'token-1', user });
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 200 }));

    await call(api.POST('/api/v1/auth/logout', { params: { header: { Authorization: '' } } }));

    const request = fetchMock.mock.calls[0]![0] as Request;
    expect(request.headers.get('Authorization')).toBe('Bearer token-1');
    expect(fetchMock).toHaveBeenCalledTimes(1); // 401 → 재발급이 일어나지 않음
  });

  it('재발급을 기다리는 사이 로그아웃하면 늦게 온 재발급 결과로 로그인 상태가 되살아나지 않는다', async () => {
    let finishReissue!: (res: Response) => void;
    fetchMock.mockReturnValueOnce(new Promise<Response>((resolve) => (finishReissue = resolve)));

    const refreshing = refreshAccessToken();
    setSession(null); // 로그아웃
    finishReissue(json(200, { success: true, data: { accessToken: 'late-token', expiresIn: 1800, user } }));

    await expect(refreshing).resolves.toBeNull();
    expect(getAccessToken()).toBeNull();
  });

  it('401이면 재발급한 뒤 원래 요청을 새 토큰으로 다시 보낸다', async () => {
    setSession({ accessToken: 'expired', user });
    fetchMock
      .mockResolvedValueOnce(json(401, { success: false, error: { code: 'AUTH_001', message: '만료' } }))
      .mockResolvedValueOnce(json(200, { success: true, data: { accessToken: 'token-2', expiresIn: 1800, user } }))
      .mockResolvedValueOnce(json(200, { success: true, data: { id: 7 } }));

    const data = await call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId: 7 } } }));

    expect(data).toEqual({ id: 7 });
    expect(getAccessToken()).toBe('token-2');
    expect(fetchMock.mock.calls[1]![0]).toBe('/api/v1/auth/reissue');
    const retried = fetchMock.mock.calls[2]![0] as Request;
    expect(retried.headers.get('Authorization')).toBe('Bearer token-2');
  });

  it('재발급에 실패하면 로그아웃 상태가 되고 401 오류를 던진다', async () => {
    setSession({ accessToken: 'expired', user });
    fetchMock
      .mockResolvedValueOnce(json(401, { success: false, error: { code: 'AUTH_001', message: '로그인이 필요합니다.' } }))
      .mockResolvedValueOnce(json(401, { success: false, error: { code: 'AUTH_002', message: '만료' } }));

    const promise = call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId: 7 } } }));

    await expect(promise).rejects.toMatchObject({ code: 'AUTH_001', status: 401 });
    expect(getAccessToken()).toBeNull();
  });

  it('재발급이 서버 오류(5xx)나 네트워크 오류로 실패하면 지금 세션을 지우지 않는다', async () => {
    setSession({ accessToken: 'token-1', user });
    fetchMock.mockResolvedValueOnce(json(503, { success: false, error: { code: 'COMMON_500', message: '점검 중' } }));
    await expect(refreshAccessToken()).resolves.toBeNull();
    expect(getAccessToken()).toBe('token-1');

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await expect(refreshAccessToken()).resolves.toBeNull();
    expect(getAccessToken()).toBe('token-1');
  });

  it('실패 응답은 서버의 에러 코드와 메시지를 담은 ApiError가 된다', async () => {
    fetchMock.mockResolvedValueOnce(json(404, { success: false, error: { code: 'AUCTION_001', message: '경매가 존재하지 않습니다.' } }));

    const error = await call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId: 9 } } })).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ code: 'AUCTION_001', status: 404 });
    expect(errorMessage(error)).toBe('경매가 존재하지 않습니다.');
  });

  it('서버에 연결하지 못하면 네트워크 오류 문구를 보여준다', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));

    const error = await call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId: 9 } } })).catch((e) => e);

    expect(errorMessage(error)).toBe('서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.');
  });
});
