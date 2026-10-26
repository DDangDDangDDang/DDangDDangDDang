import { useInfiniteQuery } from '@tanstack/react-query';
import { api, call, errorMessage, type Schema } from '@/api';
import { Badge, Button, Card, CardBody, Notice, Skeleton } from '@/components/ui';
import { formatDateTime, formatNumber, formatPoint } from '@/lib/format';
import { auctionKey } from '@/lib/use-auction-realtime';

type BidRow = Schema<'BidHistoryResponse'>;

const PAGE_SIZE = 10;

/**
 * 경매의 입찰 기록 (최신순). 로그인하지 않아도 보인다.
 * 입찰자는 가린 닉네임으로 보이고, 내 입찰에는 [나]를 붙인다.
 * 쿼리 키가 경매 키 아래라 실시간 입찰 메시지가 오면 함께 다시 불러온다. (useAuctionRealtime)
 */
export function BidHistory({ auctionId, bidCount }: { auctionId: number; bidCount: number }) {
  const query = useInfiniteQuery({
    queryKey: [...auctionKey(auctionId), 'bids'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/auctions/{auctionId}/bids', { params: { path: { auctionId }, query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const rows = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <Card>
      <CardBody className="flex flex-col gap-3 pt-5">
        <h2 className="flex items-baseline gap-2 text-title-md font-bold">
          입찰 기록
          <span className="text-label font-semibold text-neutral-500">
            <span className="num">{formatNumber(bidCount)}</span>회
          </span>
        </h2>
        {query.isPending ? (
          <div className="flex flex-col gap-2" aria-busy="true">
            <Skeleton className="h-10" />
            <Skeleton className="h-10" />
          </div>
        ) : query.isError && rows.length === 0 ? (
          <Notice tone="error">
            입찰 기록을 불러오지 못했어요. {errorMessage(query.error)}{' '}
            <button type="button" onClick={() => void query.refetch()} className="font-semibold underline">
              다시 불러오기
            </button>
          </Notice>
        ) : rows.length === 0 ? (
          <p className="text-body text-neutral-500">아직 입찰이 없어요. 첫 입찰자가 되어 보세요.</p>
        ) : (
          <ol aria-label="입찰 기록" className="flex flex-col divide-y divide-neutral-100">
            {rows.map((row) => (
              <BidHistoryRow key={row.bidId} row={row} />
            ))}
          </ol>
        )}
        {query.hasNextPage && (
          <Button variant="ghost" size="sm" className="self-center" loading={query.isFetchingNextPage} onClick={() => void query.fetchNextPage()}>
            이전 입찰 더 보기
          </Button>
        )}
      </CardBody>
    </Card>
  );
}

function BidHistoryRow({ row }: { row: BidRow }) {
  return (
    <li className="flex items-center justify-between gap-4 py-2.5">
      <span className="flex min-w-0 items-center gap-2">
        <span className="truncate text-body font-semibold">{row.bidderNickname || '알 수 없음'}</span>
        {row.mine && <Badge variant="outline">나</Badge>}
        {row.status === 'ACTIVE' && <Badge variant="info">최고 입찰</Badge>}
        {row.status === 'WON' && <Badge variant="neutral">낙찰</Badge>}
      </span>
      <span className="flex shrink-0 items-baseline gap-3">
        <span className="text-caption text-neutral-500">{row.createdAt ? formatDateTime(row.createdAt) : ''}</span>
        <strong className="num text-body font-bold">{formatPoint(row.amount ?? 0)}</strong>
      </span>
    </li>
  );
}
