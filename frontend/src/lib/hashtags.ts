/*
 * 방송 해시태그 규칙. 서버(LiveStreamHashtags)와 같은 규칙으로 입력 단계에서 먼저 알려 준다.
 * 화면에는 "#나이키"처럼 #을 붙여 보여 주고, 서버에는 # 없이 보낸다.
 */

export const HASHTAG_MAX_COUNT = 5;
export const HASHTAG_MAX_LENGTH = 20;

const TAG = new RegExp(`^[\\p{L}\\p{N}_]{1,${HASHTAG_MAX_LENGTH}}$`, 'u');

export type HashtagAddResult = { tags: string[]; error?: string };

/**
 * 입력한 글을 태그 목록에 더한다. 앞의 #과 앞뒤 공백은 뺀다.
 * 빈 글이면 그대로, 대소문자만 다른 같은 태그가 이미 있으면 더하지 않는다.
 */
export function addHashtag(tags: string[], input: string): HashtagAddResult {
  const tag = input.trim().replace(/^#+/, '').trim();
  if (!tag) return { tags };
  if (!TAG.test(tag)) return { tags, error: `해시태그는 한글·영문·숫자·밑줄(_)로 ${HASHTAG_MAX_LENGTH}자까지 쓸 수 있어요.` };
  if (tags.some((t) => t.toLowerCase() === tag.toLowerCase())) return { tags };
  if (tags.length >= HASHTAG_MAX_COUNT) return { tags, error: `해시태그는 ${HASHTAG_MAX_COUNT}개까지 넣을 수 있어요.` };
  return { tags: [...tags, tag] };
}

export type HashtagDraftResult = { tags: string[]; draft: string; error?: string };

/**
 * 입력칸의 글에 쉼표·띄어쓰기가 있으면(붙여 넣기, 한글 조합 중에 친 구분자) 그 앞까지를 태그로 더하고 마지막 조각만 입력칸에 남긴다.
 * 더하다 오류가 나면 거기서 멈추고, 아직 못 더한 글을 입력칸에 남겨 고칠 수 있게 한다.
 */
export function splitHashtagDraft(tags: string[], value: string): HashtagDraftResult {
  const parts = value.split(/[,\s]/);
  const rest = parts.pop() ?? '';
  let current = tags;
  for (let i = 0; i < parts.length; i++) {
    const result = addHashtag(current, parts[i] ?? '');
    if (result.error) return { tags: current, draft: [...parts.slice(i), rest].join(','), error: result.error };
    current = result.tags;
  }
  return { tags: current, draft: rest };
}

/** 두 태그 목록이 순서까지 같은지 (수정할 때 바뀐 것만 보내기) */
export function sameHashtags(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((tag, i) => tag === b[i]);
}
