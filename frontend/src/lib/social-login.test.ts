import { beforeEach, describe, expect, it } from 'vitest';
import { isSocialProvider, socialAuthorizeUrl, socialConfig, takeSocialState } from './social-login';

describe('socialConfig', () => {
  it('클라이언트 ID가 없으면 설정이 없는 것으로 본다', () => {
    expect(socialConfig('kakao', {}, 'http://localhost:5173')).toBeUndefined();
    expect(socialConfig('google', { VITE_GOOGLE_CLIENT_ID: ' ' }, 'http://localhost:5173')).toBeUndefined();
  });

  it('제공자마다 자기 환경변수를 읽고, redirect URI가 없으면 지금 주소의 제공자 콜백 경로를 쓴다', () => {
    const env = { VITE_KAKAO_REST_API_KEY: 'kakao-key', VITE_GOOGLE_CLIENT_ID: 'google-id' };
    expect(socialConfig('kakao', env, 'http://localhost:5174')).toEqual({
      clientId: 'kakao-key',
      redirectUri: 'http://localhost:5174/oauth/kakao/callback',
    });
    expect(socialConfig('google', env, 'http://localhost:5174')).toEqual({
      clientId: 'google-id',
      redirectUri: 'http://localhost:5174/oauth/google/callback',
    });
    expect(socialConfig('google', { ...env, VITE_GOOGLE_REDIRECT_URI: 'https://ddang.app/oauth/google/callback' })?.redirectUri).toBe(
      'https://ddang.app/oauth/google/callback',
    );
  });

  it('지원하는 제공자 이름만 인정한다', () => {
    expect(isSocialProvider('kakao')).toBe(true);
    expect(isSocialProvider('google')).toBe(true);
    expect(isSocialProvider('naver')).toBe(false);
    expect(isSocialProvider(undefined)).toBe(false);
  });
});

describe('socialAuthorizeUrl / takeSocialState', () => {
  const kakao = { clientId: 'kakao-key', redirectUri: 'http://localhost:5173/oauth/kakao/callback' };
  const google = { clientId: 'google-id', redirectUri: 'http://localhost:5173/oauth/google/callback' };

  beforeEach(() => sessionStorage.clear());

  it('카카오 로그인 주소에 키·redirect URI·state를 담는다', () => {
    const url = new URL(socialAuthorizeUrl('kakao', kakao, '/auctions/1'));

    expect(url.origin + url.pathname).toBe('https://kauth.kakao.com/oauth/authorize');
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('client_id')).toBe('kakao-key');
    expect(url.searchParams.get('redirect_uri')).toBe(kakao.redirectUri);
    expect(url.searchParams.get('state')).toMatch(/^[0-9a-f]{32}$/);
    expect(url.searchParams.get('scope')).toBeNull();
  });

  it('구글 로그인 주소에는 openid email profile 범위를 담는다', () => {
    const url = new URL(socialAuthorizeUrl('google', google, '/'));

    expect(url.origin + url.pathname).toBe('https://accounts.google.com/o/oauth2/v2/auth');
    expect(url.searchParams.get('client_id')).toBe('google-id');
    expect(url.searchParams.get('scope')).toBe('openid email profile');
  });

  it('제공자와 state가 같으면 돌아갈 주소를 주고, 한 번만 쓸 수 있다', () => {
    const state = new URL(socialAuthorizeUrl('kakao', kakao, '/auctions/1')).searchParams.get('state');

    expect(takeSocialState('kakao', state)).toEqual({ redirect: '/auctions/1' });
    expect(takeSocialState('kakao', state)).toBeUndefined();
  });

  it('state가 다르거나, 다른 제공자로 시작한 state면 거절한다', () => {
    socialAuthorizeUrl('kakao', kakao, '/');
    expect(takeSocialState('kakao', 'forged')).toBeUndefined();

    const state = new URL(socialAuthorizeUrl('kakao', kakao, '/')).searchParams.get('state');
    expect(takeSocialState('google', state)).toBeUndefined();
  });

  it('시작한 적이 없으면 거절한다', () => {
    expect(takeSocialState('kakao', 'anything')).toBeUndefined();
  });
});
