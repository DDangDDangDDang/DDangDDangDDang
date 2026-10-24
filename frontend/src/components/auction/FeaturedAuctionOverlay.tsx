import type { Schema } from '@/api';
import { Button } from '@/components/ui';
import { auctionPhase, featuredAuctions } from '@/lib/auction';
import { cn } from '@/lib/cn';
import { formatNumber, formatPoint } from '@/lib/format';
import { useNow } from '@/lib/use-now';
import { AuctionPhaseBadge, AuctionTime } from './AuctionBits';

type Props = {
  auction: Schema<'AuctionResponse'>;
  /** 시청 화면: 누르면 입찰 창을 연다. 없으면(스튜디오 미리보기) 버튼을 그리지 않는다. */
  onOpen?: (auctionId: number, ongoing: boolean) => void;
  className?: string;
};

/**
 * 방송 정보의 featuredAuctionIds(최대 2개)를 방송 경매 목록에서 찾아 띄운 순서대로 위에서부터 쌓는다.
 * 끝났거나 취소된 경매는 그리지 않는다. 위치는 className으로 정한다.
 * 매초 상태를 다시 판단하므로 화면 전체가 아니라 이 부품만 다시 그려지게 따로 둔다.
 */
export function FeaturedAuctionSlot({
  featuredAuctionIds,
  auctions,
  className,
  ...props
}: Omit<Props, 'auction'> & { featuredAuctionIds?: number[]; auctions: Schema<'AuctionResponse'>[] }) {
  const now = useNow((featuredAuctionIds ?? []).length === 0 ? null : 1000);
  const shown = featuredAuctions(featuredAuctionIds, auctions, now);
  if (shown.length === 0) return null;
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      {shown.map((auction) => (
        <FeaturedAuctionOverlay key={auction.id} auction={auction} {...props} />
      ))}
    </div>
  );
}

/**
 * 판매자가 영상 위에 띄운 경매 하나. 상품명·현재가(시작 전이면 시작가)·입찰 수·남은 시간.
 * 시청 화면과 스튜디오 미리보기가 같이 써서 판매자가 시청자에게 보이는 모습을 그대로 확인한다.
 * 영상 안에 있어 전체 화면에서도 보인다. 위치는 화면마다 className으로 정한다.
 */
export function FeaturedAuctionOverlay({ auction, onOpen, className }: Props) {
  const now = useNow(1000);
  const phase = auctionPhase(auction, now);
  const ongoing = phase === 'ONGOING';
  const name = auction.productName || '이름 없는 상품';

  return (
    <div
      role="region"
      aria-label="영상 위 경매"
      aria-live="polite"
      className={cn('flex w-[320px] flex-col gap-2 rounded-lg bg-neutral-0/95 px-4 py-3 text-neutral-900 shadow-md', className)}
    >
      {/* 시작 시각("10월 22일 (목) 02:06 시작")이 길어 상품명은 한 줄을 다 쓰고, 시간은 아래 줄 오른쪽에 둔다. */}
      <div className="flex min-w-0 items-center gap-1.5">
        <AuctionPhaseBadge phase={phase} />
        <strong className="truncate text-body font-semibold">{name}</strong>
      </div>
      <div className="flex items-end justify-between gap-2">
        <div className="flex flex-col">
          <span className="text-caption text-neutral-500">{phase === 'READY' ? '시작가' : '현재가'}</span>
          <span className="num text-price-lg leading-tight font-bold">{formatPoint(auction.currentPrice ?? auction.startPrice ?? 0)}</span>
          <span className="text-caption text-neutral-500">
            입찰 <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
          </span>
        </div>
        <div className="flex flex-col items-end gap-1.5">
          <span className="whitespace-nowrap">
            <AuctionTime status={auction.status} startAt={auction.startAt} endAt={auction.endAt} />
          </span>
          {onOpen && auction.id !== undefined && (phase === 'READY' || ongoing) && (
            <Button size="sm" variant={ongoing ? 'primary' : 'outline'} aria-label={`${name} ${ongoing ? '입찰하기' : '경매 보기'}`} onClick={() => auction.id && onOpen(auction.id, ongoing)}>
              {ongoing ? '입찰하기' : '경매 보기'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
