import { cn } from '@/lib/cn';

/** 방송 해시태그 표시 (#태그). 방송 목록 카드·시청 화면·스튜디오가 같이 쓴다. 태그가 없으면 그리지 않는다. */
export function Hashtags({ tags, className }: { tags?: string[]; className?: string }) {
  if (!tags || tags.length === 0) return null;
  return (
    <ul aria-label="해시태그" className={cn('flex flex-wrap gap-x-2 gap-y-0.5 text-label font-semibold text-brand-700', className)}>
      {tags.map((tag) => (
        <li key={tag}>#{tag}</li>
      ))}
    </ul>
  );
}
