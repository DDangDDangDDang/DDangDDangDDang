import { Star } from 'lucide-react';
import { useId, useRef, type KeyboardEvent } from 'react';
import { cn } from '@/lib/cn';

const SCORES = [1, 2, 3, 4, 5] as const;

/** 별점 표시 (채운 별 = 점수). 화면 읽기 프로그램에는 "별점 5점 만점에 4점"으로 읽힌다. */
export function StarRating({ rating, className }: { rating: number; className?: string }) {
  return (
    <span role="img" aria-label={`별점 5점 만점에 ${rating}점`} className={cn('inline-flex shrink-0 items-center gap-0.5', className)}>
      {SCORES.map((score) => (
        <Star
          key={score}
          aria-hidden="true"
          className={cn('size-3.5', score <= rating ? 'fill-current text-brand-500' : 'text-neutral-200')}
        />
      ))}
    </span>
  );
}

/**
 * 별점 고르기 (1~5). 라디오 그룹이라 좌우 화살표로도 바꿀 수 있다.
 * value가 0이면 아직 고르지 않은 상태다.
 */
export function RatingInput({ value, onChange, label, error }: { value: number; onChange: (value: number) => void; label: string; error?: string }) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const labelId = useId();
  const move = (event: KeyboardEvent<HTMLButtonElement>) => {
    const current = value || 1;
    const next = { ArrowRight: Math.min(5, current + 1), ArrowUp: Math.min(5, current + 1), ArrowLeft: Math.max(1, current - 1), ArrowDown: Math.max(1, current - 1) }[event.key];
    if (next === undefined) return;
    event.preventDefault();
    onChange(next);
    refs.current[next - 1]?.focus();
  };

  return (
    <div className="flex flex-col gap-1.5">
      <span id={labelId} className="text-label font-semibold text-neutral-900">
        {label}
      </span>
      <div role="radiogroup" aria-labelledby={labelId} className="flex items-center gap-1">
        {SCORES.map((score) => (
          <button
            key={score}
            ref={(el) => {
              refs.current[score - 1] = el;
            }}
            type="button"
            role="radio"
            aria-checked={value === score}
            aria-label={`${score}점`}
            // 고른 별만 Tab으로 들어간다. 아직 안 골랐으면 첫 별.
            tabIndex={value === score || (value === 0 && score === 1) ? 0 : -1}
            onClick={() => onChange(score)}
            onKeyDown={move}
            className="rounded-sm p-0.5"
          >
            <Star aria-hidden="true" className={cn('size-8', score <= value ? 'fill-current text-brand-500' : 'text-neutral-400')} />
          </button>
        ))}
        <span className="ml-2 num text-body text-neutral-500">{value ? `${value}점` : '별점을 골라 주세요'}</span>
      </div>
      {error && <p className="text-caption text-red-300">{error}</p>}
    </div>
  );
}
