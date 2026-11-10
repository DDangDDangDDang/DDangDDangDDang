import { Gavel } from 'lucide-react';
import { Badge } from '@/components/ui';
import { cn } from '@/lib/cn';
import { HAMMER_GRADE_LABEL, type HammerGrade } from '@/lib/labels';

const GRADE_CLASS: Record<HammerGrade, string> = {
  NEWBIE: 'text-neutral-500',
  WOOD: 'text-grade-wood',
  STONE: 'text-grade-stone',
  SILVER: 'text-grade-silver',
  GOLD: 'text-grade-gold',
  DIAMOND: 'text-grade-diamond',
};

const DESCRIPTION = '최근 12개월의 구매 확정 거래, 받은 리뷰, 본인 책임 취소로 정한 등급입니다. 구매 확정 거래가 3건 미만이면 새 망치입니다.';

/** 망치 등급: 거래 신뢰도를 단계로 보여 준다. (점수 숫자는 보여 주지 않는다) */
export function HammerGradeBadge({ grade, className }: { grade?: HammerGrade; className?: string }) {
  if (!grade) return null;
  return (
    <Badge variant="outline" title={DESCRIPTION} className={cn(GRADE_CLASS[grade], className)}>
      <Gavel className="size-3.5" aria-hidden="true" />
      {HAMMER_GRADE_LABEL[grade]}
    </Badge>
  );
}
