import { X } from 'lucide-react';
import { useState, type KeyboardEvent } from 'react';
import { Input } from '@/components/ui';
import { addHashtag, HASHTAG_MAX_COUNT } from '@/lib/hashtags';

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

  const commit = () => {
    const result = addHashtag(tags, draft);
    setInputError(result.error);
    if (result.error) return;
    setDraft('');
    if (result.tags !== tags) onChange(result.tags);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // 한글 조합 중 Enter는 글자 확정이라 태그로 더하지 않는다.
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
        value={draft}
        disabled={full}
        maxLength={30}
        onChange={(e) => {
          setDraft(e.target.value);
          setInputError(undefined);
        }}
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
