import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';
import { useSellerProfile } from '@/lib/use-seller';
import { SellerAvatar } from './SellerAvatar';
import { useOpenSellerProfile } from './seller-profile-context';

type Props = {
  sellerId?: number;
  /** 프로필을 불러오기 전에 보여줄 이름 (방송 카드 등에서 받은 값) */
  fallbackName?: string;
  size?: 'sm' | 'md';
  /** 이름 아래 한 줄 (방송 시작 시각 등) */
  detail?: ReactNode;
  /** 방송 중이면 썸네일에 주황 테두리 */
  live?: boolean;
  className?: string;
};

/**
 * 판매자 썸네일 + 이름. 판매자 인증 회원이면 인증 마크를 붙이고, 누르면 판매자 프로필 창이 열린다.
 * 인증 전인 회원은 프로필 창 없이 이름만 보여 준다.
 */
export function SellerIdentity({ sellerId, fallbackName, size = 'md', detail, live = false, className }: Props) {
  const profile = useSellerProfile(sellerId);
  const openProfile = useOpenSellerProfile();
  const name = profile.data?.nickname || fallbackName || '판매자';
  const verified = profile.data?.verifiedSeller ?? false;

  const content = (
    <>
      <SellerAvatar name={name} verified={verified} size={size} live={live} />
      <span className="flex min-w-0 flex-col text-left">
        <strong className={cn('truncate font-semibold', size === 'sm' ? 'text-body' : 'text-body-lg', verified && 'group-hover:underline')}>{name}</strong>
        {detail && <span className="text-label text-neutral-500">{detail}</span>}
      </span>
    </>
  );

  if (verified && sellerId !== undefined) {
    return (
      <button type="button" aria-label={`${name} 판매자 프로필 보기`} onClick={() => openProfile(sellerId)} className={cn('group flex min-w-0 items-center gap-3', className)}>
        {content}
      </button>
    );
  }
  return <span className={cn('flex min-w-0 items-center gap-3', className)}>{content}</span>;
}
