import { Clock, Radio } from 'lucide-react';
import { Link } from 'react-router';
import type { Schema } from '@/api';
import { ProductImage } from '@/components/order/OrderBits';
import { Badge, Skeleton, type BadgeProps } from '@/components/ui';
import { AUCTION_PHASE_LABEL, auctionPhase, secondsUntil, type AuctionPhase } from '@/lib/auction';
import { formatDateTime, formatNumber, formatPoint, formatRemaining } from '@/lib/format';
import { useNow } from '@/lib/use-now';

const PHASE_BADGE: Record<AuctionPhase, NonNullable<BadgeProps['variant']>> = {
  READY: 'outline',
  ONGOING: 'live',
  CLOSING: 'warning',
  ENDED: 'neutral',
  FAILED: 'neutral',
  CANCELED: 'neutral',
};

/** 경매 상태 배지. 진행 중인데 마감 시각이 지났으면 "마감"으로 보여준다. */
export function AuctionPhaseBadge({ phase, className }: { phase?: AuctionPhase; className?: string }) {
  if (!phase) return null;
  return (
    <Badge variant={PHASE_BADGE[phase]} className={className}>
      {AUCTION_PHASE_LABEL[phase]}
    </Badge>
  );
}

/** 남은 시간 (진행 중) 또는 시작·마감 시각 */
export function AuctionTime({ status, startAt, endAt }: { status?: Schema<'AuctionResponse'>['status']; startAt?: string; endAt?: string }) {
  const now = useNow(status === 'ONGOING' ? 1000 : null);
  const phase = auctionPhase({ status, endAt }, now);
  if (phase === 'ONGOING') {
    const left = secondsUntil(endAt, now);
    return (
      <span className="flex items-center gap-1 text-label font-semibold text-brand-700">
        <Clock className="size-3.5" aria-hidden="true" />
        <span className="num">{formatRemaining(left)}</span> 남음
      </span>
    );
  }
  if (phase === 'READY' && startAt) return <span className="text-label text-neutral-500">{formatDateTime(startAt)} 시작</span>;
  if (endAt) return <span className="text-label text-neutral-500">{formatDateTime(endAt)} 마감</span>;
  return null;
}

type ListItem = Schema<'AuctionListResponse'>;

/** 경매 카드 (경매 목록, 홈 인기 경매). 누르면 경매 상세로 간다. 상품 이미지는 아직 API에 없어 자리만 그린다. */
export function AuctionCard({ auction }: { auction: ListItem }) {
  const now = useNow(auction.status === 'ONGOING' ? 1000 : null);
  const phase = auctionPhase(auction, now);

  return (
    <Link to={`/auctions/${auction.auctionId}`} className="group flex flex-col gap-3 rounded-lg">
      <div className="relative">
        <ProductImage alt={auction.productName ?? '상품'} className="aspect-square w-full rounded-lg" />
        <div className="absolute top-3 left-3 flex gap-1.5">
          <AuctionPhaseBadge phase={phase} />
          {auction.live && (
            <Badge className="bg-stage/70 font-semibold text-neutral-0">
              <Radio className="size-3.5" aria-hidden="true" />
              방송 중 경매
            </Badge>
          )}
        </div>
      </div>
      <div className="flex flex-col gap-1">
        {auction.categoryName && <span className="text-caption text-neutral-500">{auction.categoryName}</span>}
        <strong className="line-clamp-2 text-body-lg font-semibold group-hover:underline">{auction.productName || '이름 없는 상품'}</strong>
        <span className="flex items-baseline gap-1.5">
          <span className="text-caption text-neutral-500">{phase === 'ENDED' ? '낙찰가' : '현재가'}</span>
          <span className="num text-title-md font-bold">{formatPoint(auction.currentPrice ?? 0)}</span>
        </span>
        <span className="flex items-center gap-2">
          <span className="text-label text-neutral-500">
            입찰 <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
          </span>
          <AuctionTime status={auction.status} endAt={auction.endAt} />
        </span>
      </div>
    </Link>
  );
}

export function AuctionCardSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <Skeleton className="aspect-square w-full rounded-lg" />
      <Skeleton className="h-5 w-3/4" />
      <Skeleton className="h-6 w-1/2" />
    </div>
  );
}
