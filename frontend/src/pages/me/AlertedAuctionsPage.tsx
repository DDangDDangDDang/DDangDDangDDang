import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { BellOff } from 'lucide-react';
import { Link, useSearchParams } from 'react-router';
import { api, call, errorMessage, type paths } from '@/api';
import { AuctionCard, AuctionCardSkeleton } from '@/components/auction/AuctionBits';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, buttonVariants, EmptyState, ErrorState, Notice } from '@/components/ui';
import { cn } from '@/lib/cn';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

const PAGE_SIZE = 20;
const ALERTS_KEY = ['auction-alerts', 'mine'] as const;

/** 요청 파라미터 filter (ACTIVE: 시작 전·진행 중, ENDED: 낙찰·유찰·취소). 명세에서 생성된 타입 */
type Filter = NonNullable<NonNullable<paths['/api/v1/users/me/auction-alerts']['get']['parameters']['query']>['filter']>;

const TABS: { key: Filter; label: string; empty: string; emptyDescription: string }[] = [
  {
    key: 'ACTIVE',
    label: '진행 중·예정',
    empty: '알림 받는 경매가 없어요',
    emptyDescription: '경매 상세에서 [알림 받기]를 누르면 여기에 모이고, 시작·마감 임박을 놓치지 않게 알려 드려요.',
  },
  {
    key: 'ENDED',
    label: '끝난 경매',
    empty: '끝난 경매가 없어요',
    emptyDescription: '낙찰·유찰·취소된 경매는 결과를 볼 수 있게 7일 동안 여기에 남아요.',
  },
];

/**
 * 알림 받는 경매: 경매 상세에서 [알림 받기]를 누른 경매를 최근에 받기로 한 것부터 모아 본다. (관심 경매)
 * [진행 중·예정] / [끝난 경매] 탭으로 나누고, 끝난 경매는 7일 뒤 자동으로 빠진다. 카드마다 [알림 끄기]로 바로 뺄 수 있다.
 */
export function AlertedAuctionsPage() {
  const [params, setParams] = useSearchParams();
  const filter: Filter = params.get('filter') === 'ended' ? 'ENDED' : 'ACTIVE';
  const tab = TABS.find((item) => item.key === filter);
  const queryClient = useQueryClient();

  const query = useInfiniteQuery({
    queryKey: [...ALERTS_KEY, filter],
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/users/me/auction-alerts', { params: { query: { filter, cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const auctions = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  const turnOff = useMutation({
    mutationFn: (auctionId: number) => call(api.DELETE('/api/v1/auctions/{auctionId}/alert', { params: { path: { auctionId } } })),
    onSuccess: (_, auctionId) => {
      void queryClient.invalidateQueries({ queryKey: ALERTS_KEY });
      void queryClient.invalidateQueries({ queryKey: ['auction-alert', auctionId] });
    },
  });

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
      <div role="tablist" aria-label="경매 상태" className="mb-5 flex w-fit gap-1 rounded-md bg-neutral-50 p-1">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            id={`alerted-auctions-tab-${item.key}`}
            aria-controls="alerted-auctions-panel"
            aria-selected={filter === item.key}
            onClick={() => setParams(item.key === 'ENDED' ? { filter: 'ended' } : {}, { replace: true })}
            className={cn(
              'h-9 rounded-sm px-4 text-body font-semibold transition-colors',
              filter === item.key ? 'bg-neutral-0 text-neutral-900 shadow-sm' : 'text-neutral-500 hover:text-neutral-900',
            )}
          >
            {item.label}
          </button>
        ))}
      </div>
      {/* 선택한 탭의 목록 */}
      <div role="tabpanel" id="alerted-auctions-panel" aria-labelledby={`alerted-auctions-tab-${filter}`}>
      {turnOff.isError && (
        <Notice tone="error" className="mb-4">
          {errorMessage(turnOff.error)}
        </Notice>
      )}
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
          title={tab?.empty ?? ''}
          description={tab?.emptyDescription}
          action={
            filter === 'ACTIVE' && (
              <Link to="/auctions" className={buttonVariants({ variant: 'primary', size: 'sm' })}>
                경매 둘러보기
              </Link>
            )
          }
        />
      ) : (
        <Grid>
          {auctions.map((auction) => (
            <div key={auction.auctionId} className="flex flex-col gap-2">
              <AuctionCard auction={auction} />
              <Button
                variant="ghost"
                size="sm"
                className="self-start"
                aria-label={`${auction.productName ?? '경매'} 알림 끄기`}
                loading={turnOff.isPending && turnOff.variables === auction.auctionId}
                onClick={() => auction.auctionId !== undefined && turnOff.mutate(auction.auctionId)}
              >
                <BellOff className="size-4" aria-hidden="true" />
                알림 끄기
              </Button>
            </div>
          ))}
        </Grid>
      )}
      {/* 다음 페이지만 실패하면 지금 카드는 두고 아래에서 다시 불러오게 한다. */}
      {query.isFetchNextPageError && <ErrorState message={errorMessage(query.error)} onRetry={() => void query.fetchNextPage()} />}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && (
        <Grid>
          <AuctionCardSkeleton />
        </Grid>
      )}
      </div>
    </div>
  );
}

function Grid({ children }: { children: React.ReactNode }) {
  return <div className="grid grid-cols-4 gap-x-5 gap-y-8">{children}</div>;
}
