import { describe, expect, it } from 'vitest';
import { addHashtag, sameHashtags } from './hashtags';

describe('addHashtag', () => {
  it('앞의 #과 공백을 빼고 더한다', () => {
    expect(addHashtag([], ' #나이키 ').tags).toEqual(['나이키']);
    expect(addHashtag(['나이키'], '2026_신상').tags).toEqual(['나이키', '2026_신상']);
  });

  it('빈 글이나 대소문자만 다른 같은 태그는 더하지 않고 오류도 없다', () => {
    expect(addHashtag(['Nike'], '  ')).toEqual({ tags: ['Nike'] });
    expect(addHashtag(['Nike'], '#nike')).toEqual({ tags: ['Nike'] });
  });

  it('공백·특수문자가 있거나 20자를 넘으면 오류', () => {
    expect(addHashtag([], '한정판!').error).toBeDefined();
    expect(addHashtag([], '띄어 쓴').error).toBeDefined();
    expect(addHashtag([], 'a'.repeat(21)).error).toBeDefined();
    expect(addHashtag([], 'a'.repeat(20)).tags).toHaveLength(1);
  });

  it('5개를 넘으면 오류', () => {
    const five = ['a', 'b', 'c', 'd', 'e'];
    expect(addHashtag(five, 'f')).toEqual({ tags: five, error: '해시태그는 5개까지 넣을 수 있어요.' });
  });
});

describe('sameHashtags', () => {
  it('순서까지 같아야 같다', () => {
    expect(sameHashtags(['a', 'b'], ['a', 'b'])).toBe(true);
    expect(sameHashtags(['a', 'b'], ['b', 'a'])).toBe(false);
    expect(sameHashtags([], ['a'])).toBe(false);
  });
});
