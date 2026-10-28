import { cn } from '@/lib/cn';

/**
 * 땅땅땅 로고 아이콘: 옆으로 휘두른 망치 머리가 바닥을 찍고, 닿은 자리에서 번개가 튀어 나간다. (리디자인 시안 헤더)
 * 장식이라 읽지 않는다. 이름 "땅땅땅"은 옆에 글자로 둔다.
 */
export function BrandMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" className={cn('size-8', className)}>
      <rect x="9" y="11" width="10" height="16" rx="2" className="fill-brand-500" />
      <rect x="19" y="16.5" width="12" height="4" rx="1.5" className="fill-brand-500" />
      <g fill="none" strokeLinejoin="round" strokeLinecap="round" className="stroke-brand-500">
        <path d="M9 26 L5 21 L7.5 20 L3 13" strokeWidth="1.8" />
        <path d="M8 27.5 L4 27 L5 25.5 L1 25" strokeWidth="1.6" />
        <path d="M20 27.5 L24 26 L23.5 24.5 L28 24" strokeWidth="1.6" />
      </g>
      <line x1="2" y1="29.5" x2="30" y2="29.5" strokeWidth="1.5" strokeLinecap="round" className="stroke-brand-200" />
    </svg>
  );
}
