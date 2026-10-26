import { Megaphone } from 'lucide-react';
import { Badge } from '@/components/ui';
import { ADS, AD_SIZES, type Ad, type AdPlacement } from '@/lib/ads';
import { cn } from '@/lib/cn';

/**
 * 배너 광고 자리. 자리마다 크기 비율이 정해져 있어 광고가 없어도 같은 자리를 차지한다.
 * 광고에는 "광고" 표시를 붙이고, 광고주 링크는 새 탭으로 연다. (rel="sponsored")
 * 걸린 광고가 없으면 자리와 권장 이미지 크기를 보여 준다.
 */
export function AdBanner({ placement, ad = ADS[placement], className }: { placement: AdPlacement; ad?: Ad; className?: string }) {
  const { width, height } = AD_SIZES[placement];
  const frame = cn('relative block w-full overflow-hidden rounded-lg', className);
  const aspectRatio = `${width} / ${height}`;

  if (!ad) {
    return (
      <div
        role="note"
        aria-label="광고 자리"
        style={{ aspectRatio }}
        className={cn(frame, 'flex flex-col items-center justify-center gap-1 border border-dashed border-neutral-200 bg-neutral-50 text-center text-neutral-500')}
      >
        <Megaphone className="size-5 text-neutral-400" aria-hidden="true" />
        <span className="text-label font-semibold">광고 자리</span>
        <span className="num text-caption">
          {width * 2} × {height * 2}
        </span>
      </div>
    );
  }

  return (
    <a href={ad.linkUrl} target="_blank" rel="noopener noreferrer sponsored" style={{ aspectRatio }} className={cn(frame, 'bg-neutral-50 shadow-sm')}>
      <img src={ad.imageUrl} alt={ad.alt} className="size-full object-cover" />
      <Badge className="absolute top-2 right-2 bg-stage/60 font-semibold text-white">광고</Badge>
    </a>
  );
}
