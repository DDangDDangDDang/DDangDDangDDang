import { useInfiniteQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { api, call, errorMessage } from '@/api';
import { AuctionCard, AuctionCardSkeleton } from '@/components/auction/AuctionBits';
import { PageHeader } from '@/components/layout/PageHeader';
import { buttonVariants, EmptyState, ErrorState } from '@/components/ui';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

const PAGE_SIZE = 20;

/**
 * 알림 받는 경매: 경매 상세에서 [알림 받기]를 누른 경매를 최근에 받기로 한 것부터 모아 본다. (관심 경매)
 * 시작·마감 10분 전·취소·결과를 알림으로 받는다. 끝난 경매도 결과를 볼 수 있게 남겨 둔다.
 */
export function AlertedAuctionsPage() {
  const query = useInfiniteQuery({
    queryKey: ['auction-alerts', 'mine'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/users/me/auction-alerts', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const auctions = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <div>
      <PageHeader
        title="알림 받는 경매"
        description="[알림 받기]를 누른 경매예요. 시작·마감 10분 전·취소·결과를 알림으로 알려 드려요."
        actions={
          <Link to="/me/notifications" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            알림 설정
          </Link>
        }
      />
      {query.isPending ? (
        <Grid>
          {Array.from({ length: 8 }, (_, i) => (
            <AuctionCardSkeleton key={i} />
          ))}
        </Grid>
      ) : query.isError && auctions.length === 0 ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : auctions.length === 0 ? (
        <EmptyState
          title="알림 받는 경매가 없어요"
          description="경매 상세에서 [알림 받기]를 누르면 여기에 모이고, 시작·마감 임박을 놓치지 않게 알려 드려요."
          action={
            <Link to="/auctions" className={buttonVariants({ variant: 'primary', size: 'sm' })}>
              경매 둘러보기
            </Link>
          }
        />
      ) : (
        <Grid>
          {auctions.map((auction) => (
            <AuctionCard key={auction.auctionId} auction={auction} />
          ))}
        </Grid>
      )}
      {/* 다음 페이지만 실패하면 지금 카드는 두고 아래에서 다시 불러오게 한다. */}
      {query.isFetchNextPageError && (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.fetchNextPage()} />
      )}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && (
        <Grid>
          <AuctionCardSkeleton />
        </Grid>
      )}
    </div>
  );
}

function Grid({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-4 gap-x-5 gap-y-8">{children}</div>;
}
