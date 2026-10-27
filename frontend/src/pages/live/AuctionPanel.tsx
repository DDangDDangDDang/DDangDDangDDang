import { Gavel } from 'lucide-react';
import { errorMessage } from '@/api';
import { AuctionPhaseBadge, AuctionTime } from '@/components/auction/AuctionBits';
import { Button, Card, CardBody, CardHeader, CardTitle, ErrorState, Skeleton } from '@/components/ui';
import { auctionPhase } from '@/lib/auction';
import { cn } from '@/lib/cn';
import { formatNumber, formatPoint } from '@/lib/format';
import { useLiveStreamAuctions, WATCH_AUCTION_REFRESH_MS } from '@/lib/use-live-stream-auctions';
import { useNow } from '@/lib/use-now';


/**
 * 이 방송에 올라간 경매 중 시작 전·진행 중인 것. 판매자가 경매를 열거나 방송에 올리면 바로 나온다. (경매 변경 알림)
 * 방송과 경매는 별개 도메인이라 방송 화면은 보여주기만 한다. 입찰은 방송을 떠나지 않도록 입찰 창(onOpen)에서 한다.
 */
export function AuctionPanel({ liveStreamId, onOpen }: { liveStreamId: number; onOpen: (auctionId: number, ongoing: boolean) => void }) {
  const query = useLiveStreamAuctions(liveStreamId, WATCH_AUCTION_REFRESH_MS);
  const now = useNow(1000);
  const open = (query.data ?? []).filter((a) => {
    const phase = auctionPhase(a, now);
    return phase === 'READY' || phase === 'ONGOING';
  });
  // 진행 중인 경매를 위에, 시작 전 경매는 아래에
  const sorted = [...open].sort((a, b) => Number(b.status === 'ONGOING') - Number(a.status === 'ONGOING'));

  return (
    <Card aria-labelledby="auction-title">
      <CardHeader>
        <CardTitle id="auction-title" className="flex items-center gap-2">
          이 방송의 경매 {open.length > 0 && <span className="num text-brand-700">{open.length}</span>}
        </CardTitle>
      </CardHeader>
      <CardBody className="pt-2">
        {query.isPending ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-24" />
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : sorted.length === 0 ? (
          <div className="flex flex-col items-center gap-2 py-2 text-center">
            <span className="flex size-12 items-center justify-center rounded-lg bg-neutral-50 text-neutral-400">
              <Gavel className="size-6" aria-hidden="true" />
            </span>
            <p className="text-body font-semibold">아직 올라온 경매가 없어요</p>
            <p className="text-label text-neutral-500">판매자가 경매를 시작하면 여기에 바로 나와요.</p>
          </div>
        ) : (
          <ul aria-live="polite" aria-label="이 방송의 경매" className="flex max-h-[360px] flex-col gap-2 overflow-y-auto">
            {sorted.map((auction) => {
              const phase = auctionPhase(auction, now);
              return (
                <li
                  key={auction.id}
                  className={cn('flex flex-col gap-2 rounded-md px-3 py-3', phase === 'ONGOING' ? 'border border-brand-200 bg-brand-50' : 'bg-neutral-25')}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="flex min-w-0 items-center gap-1.5">
                      <AuctionPhaseBadge phase={phase} />
                      <strong className="truncate text-body font-semibold">{auction.productName || '이름 없는 상품'}</strong>
                    </span>
                    {/* 상품명이 길어도 남은 시간은 한 줄로 */}
                    <span className="shrink-0 whitespace-nowrap">
                      <AuctionTime status={auction.status} startAt={auction.startAt} endAt={auction.endAt} />
                    </span>
                  </div>
                  <div className="flex items-end justify-between gap-2">
                    <div className="flex flex-col">
                      <span className="text-caption text-neutral-500">{phase === 'READY' ? '시작가' : '현재가'}</span>
                      <span className="num text-title-md font-bold">{formatPoint(auction.currentPrice ?? auction.startPrice ?? 0)}</span>
                      <span className="text-caption text-neutral-500">
                        입찰 <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
                      </span>
                    </div>
                    <Button
                      size="sm"
                      variant={phase === 'ONGOING' ? 'primary' : 'outline'}
                      aria-label={`${auction.productName || '상품'} ${phase === 'ONGOING' ? '입찰하기' : '경매 보기'}`}
                      onClick={() => auction.id && onOpen(auction.id, phase === 'ONGOING')}
                    >
                      {phase === 'ONGOING' ? '입찰하기' : '경매 보기'}
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </CardBody>
    </Card>
  );
}
