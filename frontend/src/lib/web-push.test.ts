import { describe, expect, it } from 'vitest';
import { base64UrlToBytes, sameKey } from './web-push';

describe('web-push', () => {
  it('base64url(패딩 없음) 공개키를 바이트로 바꾼다', () => {
    // 0xfb 0xff 0xbf → 표준 base64 "+/+/" 자리에 -, _ 가 온다
    expect([...base64UrlToBytes('-_-_')]).toEqual([0xfb, 0xff, 0xbf]);
    expect(base64UrlToBytes('BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4')).toHaveLength(65);
  });

  it('이미 있는 구독이 지금 서버 키로 만든 것인지 비교한다 (서버 키가 바뀌면 다시 구독)', () => {
    const key = new Uint8Array([4, 1, 2, 3]);
    expect(sameKey(new Uint8Array([4, 1, 2, 3]).buffer, key)).toBe(true);
    expect(sameKey(new Uint8Array([4, 1, 2, 9]).buffer, key)).toBe(false);
    expect(sameKey(new Uint8Array([4, 1, 2]).buffer, key)).toBe(false);
    expect(sameKey(null, key)).toBe(false);
  });
});
