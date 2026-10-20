/** 로그인 후 돌아갈 주소. 다른 사이트로 보내지 않도록 우리 경로(/로 시작)만 허용한다. */
export function safeRedirect(value: string | null): string {
  if (value && value.startsWith('/') && !value.startsWith('//')) return value;
  return '/';
}
