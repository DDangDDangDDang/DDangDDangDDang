import { BadgeCheck } from 'lucide-react';
import { cn } from '@/lib/cn';

const SIZE = {
  sm: { box: 'size-8 text-label', mark: 'size-3.5' },
  md: { box: 'size-10 text-body', mark: 'size-4' },
  lg: { box: 'size-16 text-title-md', mark: 'size-5' },
  xl: { box: 'size-24 text-title-lg', mark: 'size-6' },
} as const;

/**
 * 프로필 썸네일. 프로필 이미지(imageUrl)가 있으면 이미지, 없으면 닉네임 첫 글자.
 * 판매자 인증 회원이면 오른쪽 아래에 인증 마크를 붙인다. 마크는 색만으로 구분하지 않도록 이름("판매자 인증")을 함께 둔다.
 * 방송 중이면(live) 주황 테두리를 두른다. 테두리는 꾸밈이라 "LIVE" 글자는 쓰는 곳에서 함께 보여 준다.
 */
export function SellerAvatar({
  name,
  imageUrl,
  verified = false,
  size = 'md',
  live = false,
}: {
  name?: string;
  imageUrl?: string;
  verified?: boolean;
  size?: keyof typeof SIZE;
  live?: boolean;
}) {
  const s = SIZE[size];
  return (
    <span className="relative inline-flex shrink-0">
      <span
        aria-hidden="true"
        className={cn(
          'flex items-center justify-center overflow-hidden rounded-full bg-brand-100 font-bold text-brand-700',
          live && 'ring-2 ring-brand-500 ring-offset-2 ring-offset-neutral-25',
          s.box,
        )}
      >
        {imageUrl ? <img src={imageUrl} alt="" className="size-full object-cover" /> : (name || '판').slice(0, 1)}
      </span>
      {verified && (
        <span role="img" aria-label="판매자 인증" title="판매자 인증" className="absolute -right-0.5 -bottom-0.5 rounded-full bg-neutral-0 text-info-600">
          <BadgeCheck className={s.mark} aria-hidden="true" />
        </span>
      )}
    </span>
  );
}
