import { BadgeCheck } from 'lucide-react';
import { cn } from '@/lib/cn';

const SIZE = {
  sm: { box: 'size-8 text-label', mark: 'size-3.5' },
  md: { box: 'size-10 text-body', mark: 'size-4' },
  lg: { box: 'size-16 text-title-md', mark: 'size-5' },
} as const;

/**
 * 프로필 썸네일 (닉네임 첫 글자). 판매자 인증 회원이면 오른쪽 아래에 인증 마크를 붙인다.
 * 마크는 색만으로 구분하지 않도록 이름("판매자 인증")을 함께 둔다.
 */
export function SellerAvatar({ name, verified = false, size = 'md' }: { name?: string; verified?: boolean; size?: keyof typeof SIZE }) {
  const s = SIZE[size];
  return (
    <span className="relative inline-flex shrink-0">
      <span aria-hidden="true" className={cn('flex items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700', s.box)}>
        {(name || '판').slice(0, 1)}
      </span>
      {verified && (
        <span role="img" aria-label="판매자 인증" title="판매자 인증" className="absolute -right-0.5 -bottom-0.5 rounded-full bg-neutral-0 text-info-600">
          <BadgeCheck className={s.mark} aria-hidden="true" />
        </span>
      )}
    </span>
  );
}
