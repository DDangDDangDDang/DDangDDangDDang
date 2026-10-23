import { beforeEach, describe, expect, it } from 'vitest';
import { kakaoAuthorizeUrl, kakaoConfig, takeKakaoState } from './kakao-login';

describe('kakaoConfig', () => {
  it('REST API 키가 없으면 설정이 없는 것으로 본다', () => {
    expect(kakaoConfig({}, 'http://localhost:5173')).toBeUndefined();
    expect(kakaoConfig({ VITE_KAKAO_REST_API_KEY: ' ' }, 'http://localhost:5173')).toBeUndefined();
  });

  it('redirect URI가 없으면 지금 주소의 콜백 경로를 쓴다', () => {
    expect(kakaoConfig({ VITE_KAKAO_REST_API_KEY: 'key' }, 'http://localhost:5174')).toEqual({
      restApiKey: 'key',
      redirectUri: 'http://localhost:5174/oauth/kakao/callback',
    });
    expect(kakaoConfig({ VITE_KAKAO_REST_API_KEY: 'key', VITE_KAKAO_REDIRECT_URI: 'https://ddang.app/oauth/kakao/callback' })?.redirectUri).toBe(
      'https://ddang.app/oauth/kakao/callback',
    );
  });
});

describe('kakaoAuthorizeUrl / takeKakaoState', () => {
  const config = { restApiKey: 'key', redirectUri: 'http://localhost:5173/oauth/kakao/callback' };

  beforeEach(() => sessionStorage.clear());

  it('카카오 로그인 주소에 키·redirect URI·state를 담는다', () => {
    const url = new URL(kakaoAuthorizeUrl(config, '/auctions/1'));

    expect(url.origin + url.pathname).toBe('https://kauth.kakao.com/oauth/authorize');
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('client_id')).toBe('key');
    expect(url.searchParams.get('redirect_uri')).toBe(config.redirectUri);
    expect(url.searchParams.get('state')).toMatch(/^[0-9a-f]{32}$/);
  });

  it('state가 같으면 돌아갈 주소를 주고, 한 번만 쓸 수 있다', () => {
    const state = new URL(kakaoAuthorizeUrl(config, '/auctions/1')).searchParams.get('state');

    expect(takeKakaoState(state)).toEqual({ redirect: '/auctions/1' });
    expect(takeKakaoState(state)).toBeUndefined();
  });

  it('state가 다르거나 없으면 거절한다', () => {
    kakaoAuthorizeUrl(config, '/');
    expect(takeKakaoState('forged')).toBeUndefined();

    kakaoAuthorizeUrl(config, '/');
    expect(takeKakaoState(null)).toBeUndefined();
  });

  it('시작한 적이 없으면 거절한다', () => {
    expect(takeKakaoState('anything')).toBeUndefined();
  });
});
