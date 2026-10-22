import { X } from 'lucide-react';
import { useState, type KeyboardEvent } from 'react';
import { Input } from '@/components/ui';
import { addHashtag, HASHTAG_MAX_COUNT, splitHashtagDraft, type HashtagDraftResult } from '@/lib/hashtags';

/** 입력칸 글자 수 한도. 태그 하나는 20자까지지만, 여러 태그를 쉼표로 이어 붙여 넣을 수 있게 넉넉히 둔다. */
const DRAFT_MAX_LENGTH = 120;

type Props = {
  tags: string[];
  onChange: (tags: string[]) => void;
  /** 서버가 거절한 이유 (LIVESTREAM_015 등) */
  error?: string;
};

/**
 * 방송 해시태그 입력: 글을 쓰고 Enter·쉼표·띄어쓰기를 누르면 태그로 더한다. 입력칸을 벗어나도 더한다.
 * 더한 태그는 아래에 #태그로 보이고, [X]로 지운다. 방송 목록 카드에서 시청자에게 보인다.
 */
export function HashtagField({ tags, onChange, error }: Props) {
  const [draft, setDraft] = useState('');
  const [inputError, setInputError] = useState<string>();

  /**
   * 입력한 글을 태그로 바꾼다. 쉼표·띄어쓰기 앞까지는 태그로 더하고(붙여 넣기, 한글 조합 중에 친 구분자),
   * final이면(Enter, 입력칸 벗어남) 남은 조각까지 더한다.
   */
  const apply = (value: string, final: boolean) => {
    const split = splitHashtagDraft(tags, value);
    let next: HashtagDraftResult = split;
    if (final && !split.error) {
      const added = addHashtag(split.tags, split.draft);
      next = added.error ? { ...split, error: added.error } : { tags: added.tags, draft: '' };
    }
    setDraft(next.draft);
    setInputError(next.error);
    if (next.tags !== tags) onChange(next.tags);
  };

  const commit = () => apply(draft, true);

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // 한글 조합 중에는 키 입력을 태그 구분자로 보지 않는다. 조합 중에 친 쉼표·띄어쓰기는 조합이 끝나면 처리한다. (onCompositionEnd)
    if (e.nativeEvent.isComposing) return;
    if (e.key === 'Enter' || e.key === ',' || e.key === ' ') {
      e.preventDefault();
      commit();
    } else if (e.key === 'Backspace' && !draft && tags.length > 0) {
      onChange(tags.slice(0, -1));
    }
  };

  const full = tags.length >= HASHTAG_MAX_COUNT;

  return (
    <div className="flex flex-col gap-2">
      <Input
        label={`해시태그 (선택, ${HASHTAG_MAX_COUNT}개까지)`}
        placeholder={full ? `${HASHTAG_MAX_COUNT}개를 모두 넣었어요` : '예) 필름카메라 — Enter로 추가'}
        type="text"
        value={draft}
        disabled={full}
        maxLength={DRAFT_MAX_LENGTH}
        onChange={(e) => {
          if ((e.nativeEvent as InputEvent).isComposing) {
            setDraft(e.target.value);
            setInputError(undefined);
          } else {
            apply(e.target.value, false);
          }
        }}
        onCompositionEnd={(e) => apply(e.currentTarget.value, false)}
        onKeyDown={onKeyDown}
        onBlur={commit}
        error={inputError ?? error}
        hint={
          <span>
            방송 목록에서 시청자에게 보여요. 한글·영문·숫자·밑줄(_)로 한 개에 20자까지.{' '}
            <span className="num">
              {tags.length}/{HASHTAG_MAX_COUNT}
            </span>
          </span>
        }
      />
      {tags.length > 0 && (
        <ul aria-label="넣은 해시태그" className="flex flex-wrap gap-1.5">
          {tags.map((tag) => (
            <li key={tag} className="flex h-7 items-center gap-1 rounded-sm bg-info-50 pr-1 pl-2.5 text-label font-semibold text-info-700">
              #{tag}
              <button
                type="button"
                aria-label={`#${tag} 지우기`}
                className="flex size-5 items-center justify-center rounded-sm hover:bg-neutral-0"
                onClick={() => onChange(tags.filter((t) => t !== tag))}
              >
                <X className="size-3.5" aria-hidden="true" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
