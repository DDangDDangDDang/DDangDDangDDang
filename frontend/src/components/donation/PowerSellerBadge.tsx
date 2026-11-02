import { Crown } from 'lucide-react';
import { Badge } from '@/components/ui';
import { cn } from '@/lib/cn';

/**
 * 파워 판매자 표시: 판매자가 받은 도네이션 마일리지로 파워 판매자를 사서 기간 중이다. (라이브 목록 맨 위에 방송이 올라감)
 * onVideo는 영상·썸네일 위(어두운 배경)에, plain은 프로필 같은 밝은 배경에 쓴다.
 */
export function PowerSellerBadge({ tone = 'plain', className }: { tone?: 'plain' | 'onVideo'; className?: string }) {
  return (
    <Badge variant={tone === 'plain' ? 'warning' : undefined} className={cn(tone === 'onVideo' && 'bg-stage/70 font-semibold text-brand-300', className)}>
      <Crown className="size-3.5" aria-hidden="true" />
      파워 판매자
    </Badge>
  );
}
