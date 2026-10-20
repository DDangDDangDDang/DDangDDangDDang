/**
 * 로그인 후 돌아갈 주소. 다른 사이트로 보내지 않도록 우리 사이트 안의 경로만 허용한다.
 * - "/"로 시작해야 하고 "//"(다른 호스트)는 안 된다.
 * - 브라우저가 "/"로 바꿔 읽는 백슬래시("/\evil.com")와 제어 문자는 거부한다.
 * - 마지막으로 실제 주소를 만들어 우리 출처(origin)인지 확인한다.
 */
export function safeRedirect(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/';
  // 백슬래시, 제어 문자(U+0000~U+001F, U+007F)
  if (value.includes('\\') || [...value].some((ch) => ch.charCodeAt(0) < 0x20 || ch.charCodeAt(0) === 0x7f)) return '/';
  try {
    const url = new URL(value, window.location.origin);
    if (url.origin !== window.location.origin) return '/';
    return url.pathname + url.search + url.hash;
  } catch {
    return '/';
  }
}
