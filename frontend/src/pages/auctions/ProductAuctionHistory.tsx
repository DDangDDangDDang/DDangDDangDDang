import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api, call, errorMessage, type Schema } from '@/api';
import { AuctionPhaseBadge } from '@/components/auction/AuctionBits';
import { Card, CardBody, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { auctionPhase } from '@/lib/auction';
import { formatDate, formatNumber, formatPoint } from '@/lib/format';
import { useNow } from '@/lib/use-now';

type Auction = Schema<'AuctionResponse'>;

/**
 * 판매자 본인에게만 보이는 "이 상품의 경매 이력" (GET /products/{productId}/auctions, 최신순 최대 50개).
 * 내 상품 목록은 상품별 최신 경매 하나만 보여서, 예전에 얼마에 올렸다가 유찰·취소됐는지 여기서 보고 다시 올릴 시작가를 정한다.
 * 화면이 판매자 본인일 때만 이 부품을 그리므로, 남이 볼 때는 영역도 없고 API도 부르지 않는다.
 */
export function ProductAuctionHistory({ productId, currentAuctionId }: { productId: number; currentAuctionId: number }) {
  const now = useNow(null);
  const query = useQuery({
    queryKey: ['product-auctions', productId],
    queryFn: () => call(api.GET('/api/v1/products/{productId}/auctions', { params: { path: { productId } } })),
  });
  const auctions = query.data ?? [];

  return (
    <Card aria-labelledby="product-auction-history-title">
      <CardBody className="flex flex-col gap-3 pt-5">
        <div className="flex flex-col gap-0.5">
          <h2 id="product-auction-history-title" className="text-title-md font-bold">
            이 상품의 경매 이력
          </h2>
          <span className="text-caption text-neutral-500">판매자에게만 보여요. 다시 경매에 올릴 때 시작가를 정하는 데 참고하세요.</span>
        </div>
        {query.isPending ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : auctions.length === 0 ? (
          <EmptyState title="경매 이력이 없어요" description="이 상품으로 경매를 열면 여기에 기록이 쌓여요." />
        ) : (
          <ul aria-label="이 상품의 경매 이력" className="divide-y divide-neutral-100">
            {auctions.map((auction) => (
              <li key={auction.id}>
                <HistoryRow auction={auction} current={auction.id === currentAuctionId} now={now} />
              </li>
            ))}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}

/** 한 줄: 상태 배지·시작~마감 날짜 / 시작가·최종가·입찰 수. 다른 경매는 눌러서 그 경매 상세로 간다. */
function HistoryRow({ auction, current, now }: { auction: Auction; current: boolean; now: Date }) {
  const phase = auctionPhase(auction, now);
  const content = (
    <>
      <span className="flex min-w-0 flex-col gap-1">
        <span className="flex items-center gap-1.5">
          <AuctionPhaseBadge phase={phase} />
          {current && <span className="text-caption font-semibold text-brand-700">지금 보는 경매</span>}
        </span>
        <span className="num text-caption text-neutral-500">
          {auction.startAt ? formatDate(auction.startAt) : '-'} ~ {auction.endAt ? formatDate(auction.endAt) : '-'}
        </span>
      </span>
      <dl className="grid shrink-0 grid-cols-3 gap-4 text-right">
        <div>
          <dt className="text-caption text-neutral-500">시작가</dt>
          <dd className="num text-body font-semibold">{pointOrDash(auction.startPrice)}</dd>
        </div>
        <div>
          <dt className="text-caption text-neutral-500">최종가</dt>
          <dd className="num text-body font-bold">{pointOrDash(auction.currentPrice)}</dd>
        </div>
        <div>
          <dt className="text-caption text-neutral-500">입찰</dt>
          <dd className="text-body font-semibold">
            <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
          </dd>
        </div>
      </dl>
    </>
  );
  const rowClass = 'flex items-center justify-between gap-4 px-1 py-3';
  if (current || auction.id === undefined) return <div className={rowClass}>{content}</div>;
  return (
    <Link to={`/auctions/${auction.id}`} className={`${rowClass} rounded-md hover:bg-neutral-25`}>
      {content}
    </Link>
  );
}

/** 금액이 응답에 없으면 0 P처럼 보이지 않게 -로 */
function pointOrDash(amount?: number): string {
  return amount === undefined ? '-' : formatPoint(amount);
}
