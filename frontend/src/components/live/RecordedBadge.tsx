import { Clapperboard } from 'lucide-react';
import { Badge } from '@/components/ui';
import { cn } from '@/lib/cn';

/** 녹화 영상 방송 표시 (영상 위): 판매자가 카메라 대신 미리 녹화한 영상을 내보내는 방송 */
export function RecordedBadge({ className }: { className?: string }) {
  return (
    <Badge className={cn('bg-stage/70 font-semibold text-white', className)}>
      <Clapperboard className="size-3.5" aria-hidden="true" />
      녹화 영상
    </Badge>
  );
}
