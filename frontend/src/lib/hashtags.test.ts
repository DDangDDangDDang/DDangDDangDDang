import { describe, expect, it } from 'vitest';
import { addHashtag, sameHashtags, splitHashtagDraft } from './hashtags';

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

  it('한글·영문·숫자·밑줄 밖의 문자권(한자·가나·자모·원문자)은 오류', () => {
    for (const tag of ['漢字', 'カナ', 'ㅋㅋ', '①']) expect(addHashtag([], tag).error).toBeDefined();
    expect(addHashtag([], 'Nike_2026신상').tags).toEqual(['Nike_2026신상']);
  });

  it('5개를 넘으면 오류', () => {
    const five = ['a', 'b', 'c', 'd', 'e'];
    expect(addHashtag(five, 'f')).toEqual({ tags: five, error: '해시태그는 5개까지 넣을 수 있어요.' });
  });
});

describe('splitHashtagDraft', () => {
  it('쉼표·띄어쓰기 앞까지 태그로 더하고 마지막 조각은 입력칸에 남긴다', () => {
    expect(splitHashtagDraft([], '나이키,')).toEqual({ tags: ['나이키'], draft: '' });
    expect(splitHashtagDraft(['a'], '#운동화 한정판,신')).toEqual({ tags: ['a', '운동화', '한정판'], draft: '신' });
    expect(splitHashtagDraft([], '나이키')).toEqual({ tags: [], draft: '나이키' });
  });

  it('더하다 오류가 나면 멈추고 못 더한 글을 남긴다', () => {
    const result = splitHashtagDraft([], '나이키,한정판!,신발');
    expect(result.tags).toEqual(['나이키']);
    expect(result.draft).toBe('한정판!,신발');
    expect(result.error).toBeDefined();
  });
});

describe('sameHashtags', () => {
  it('순서까지 같아야 같다', () => {
    expect(sameHashtags(['a', 'b'], ['a', 'b'])).toBe(true);
    expect(sameHashtags(['a', 'b'], ['b', 'a'])).toBe(false);
    expect(sameHashtags([], ['a'])).toBe(false);
  });
});
