import { cva, type VariantProps } from 'class-variance-authority';
import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

const badgeVariants = cva('inline-flex h-5.5 items-center gap-1.5 rounded-sm px-2 text-caption font-bold whitespace-nowrap', {
  variants: {
    variant: {
      /** LIVE, 진행 중 경매 */
      live: 'bg-brand-500 text-white',
      /** 나의 상태: 내가 최고 입찰 */
      info: 'bg-info-50 text-info-700',
      /** 주의: 상위 입찰 있음, 오류 안내 */
      warning: 'bg-brand-50 text-brand-700',
      /** 끝난 상태: 낙찰, 종료 */
      neutral: 'bg-neutral-100 text-neutral-700',
      /** 대기 중 */
      outline: 'border border-neutral-200 text-neutral-700',
    },
  },
  defaultVariants: { variant: 'neutral' },
});

export type BadgeProps = HTMLAttributes<HTMLSpanElement> & VariantProps<typeof badgeVariants>;

/** 상태 표시. 색만으로 구분하지 않도록 반드시 글자를 함께 쓴다. */
export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />;
}
