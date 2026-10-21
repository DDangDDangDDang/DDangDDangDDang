import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Ban } from 'lucide-react';
import { Link } from 'react-router';
import { api, call, errorMessage } from '@/api';
import { Badge, Button, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatDateTime, formatNumber } from '@/lib/format';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

const PAGE_SIZE = 20;

/** 대화방 목록: 최근 대화순, 미리보기·시각·읽지 않은 수. 아래에 차단한 사람 목록. */
export function RoomList({ activeUserId }: { activeUserId?: number }) {
  const query = useInfiniteQuery({
    queryKey: ['dm', 'rooms'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/dm/rooms', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  // 다음 페이지를 못 불러오면 자동으로 다시 요청하지 않는다. ([다시 시도]로)
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const rooms = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  return (
    <aside aria-label="대화방 목록" className="flex w-[360px] shrink-0 flex-col overflow-hidden rounded-lg border border-neutral-100 bg-neutral-0 shadow-sm">
      <h1 className="border-b border-neutral-100 px-5 py-4 text-title-md font-bold">메시지</h1>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {query.isPending ? (
          <div className="flex flex-col gap-2 p-4">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-16" />
            ))}
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : rooms.length === 0 ? (
          <EmptyState title="아직 대화가 없어요" description="경매 상세나 방송 화면에서 판매자에게 문의해 보세요." />
        ) : (
          <ul>
            {rooms.map((room) => {
              const active = room.otherUser?.userId === activeUserId;
              const name = room.otherUser?.nickname || '알 수 없음';
              return (
                <li key={room.roomId}>
                  <Link
                    to={`/messages/${room.otherUser?.userId ?? ''}`}
                    aria-current={active ? 'page' : undefined}
                    className={cn('flex items-center gap-3 border-b border-neutral-100 px-5 py-3.5 transition-colors', active ? 'bg-neutral-50' : 'hover:bg-neutral-25')}
                  >
                    <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">
                      {name.slice(0, 1)}
                    </span>
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="flex items-center justify-between gap-2">
                        <strong className="truncate text-body font-semibold">{name}</strong>
                        {room.lastMessageAt && <span className="shrink-0 text-caption text-neutral-500">{formatDateTime(room.lastMessageAt)}</span>}
                      </span>
                      <span className="flex items-center justify-between gap-2">
                        <span className="truncate text-label text-neutral-500">
                          {room.blockedByMe && <Ban className="mr-1 inline size-3.5 align-[-2px]" aria-label="차단함" />}
                          {room.lastMessagePreview || ' '}
                        </span>
                        {(room.unreadCount ?? 0) > 0 && (
                          <Badge variant="live" className="num shrink-0 rounded-full">
                            <span className="sr-only">읽지 않은 메시지 </span>
                            {formatNumber(room.unreadCount ?? 0)}
                          </Badge>
                        )}
                      </span>
                    </span>
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && <Skeleton className="m-4 h-16" />}
      </div>
      <BlockedUsers />
    </aside>
  );
}

/** 차단한 사람 목록과 차단 해제. 차단하면 서로 메시지를 보낼 수 없고, 대화는 남는다. */
function BlockedUsers() {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ['dm', 'blocks'],
    queryFn: async () => (await call(api.GET('/api/v1/dm/blocks'))) ?? [],
  });
  const unblock = useMutation({
    mutationFn: (userId: number) => call(api.DELETE('/api/v1/dm/blocks/{userId}', { params: { path: { userId } } })),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['dm'] }),
  });
  const blocks = query.data ?? [];

  return (
    <details className="border-t border-neutral-100 px-5 py-3">
      <summary className="cursor-pointer text-label font-semibold text-neutral-700">
        차단한 사람 <span className="num">{query.isPending ? '' : formatNumber(blocks.length)}</span>
      </summary>
      <div className="mt-2 flex max-h-40 flex-col gap-1 overflow-y-auto">
        {query.isError ? (
          <Notice tone="error">{errorMessage(query.error)}</Notice>
        ) : blocks.length === 0 ? (
          <p className="text-caption text-neutral-500">차단한 사람이 없어요.</p>
        ) : (
          blocks.map((block) => (
            <div key={block.userId} className="flex items-center justify-between gap-2">
              <span className="truncate text-label">{block.nickname || '알 수 없음'}</span>
              <Button variant="ghost" size="sm" loading={unblock.isPending && unblock.variables === block.userId} onClick={() => block.userId && unblock.mutate(block.userId)}>
                차단 해제
              </Button>
            </div>
          ))
        )}
        {unblock.isError && <Notice tone="error">{errorMessage(unblock.error)}</Notice>}
      </div>
    </details>
  );
}
