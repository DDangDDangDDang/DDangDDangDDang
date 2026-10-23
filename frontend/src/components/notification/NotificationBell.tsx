import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, Gavel, Settings } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { Button, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatDateTime } from '@/lib/format';
import { NOTIFICATIONS_KEY } from '@/lib/notification-events';
import { notificationLink, notificationTitle } from '@/lib/notification-view';
import { useWebPush } from '@/lib/web-push';
import { BrowserPushSetting } from './BrowserPushSetting';
import { NotificationIcon } from './NotificationIcon';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';

type Notification = Schema<'NotificationResponse'>;

const PAGE_SIZE = 20;

/**
 * 헤더의 알림함: 종 아이콘 + 안 읽은 알림 수. 누르면 알림 목록이 열리고, 알림을 누르면 읽음으로 바꾸고 그 방송·경매로 간다.
 * 새 알림이 실시간으로 오면 DmRealtime이 알림 쿼리를 다시 불러온다.
 * 브라우저 알림(탭을 닫아도 오는 알림) 켜기·끄기도 여기에 둔다. 헤더에 늘 있으므로, 켜 둔 회원이 다시 접속하면 여기서 구독을 맞춘다.
 */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const session = useSession();
  const push = useWebPush(session?.user.userId);
  const rootRef = useRef<HTMLDivElement>(null);
  const unread = useQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'unread'],
    queryFn: () => call(api.GET('/api/v1/notifications/unread-count')),
  });
  const count = unread.data?.count ?? 0;

  // 바깥을 누르거나 Esc를 누르면 닫는다.
  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('pointerdown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-label={count > 0 ? `알림, 안 읽은 알림 ${count}개` : '알림'}
        aria-expanded={open}
        aria-haspopup="true"
        onClick={() => setOpen((v) => !v)}
        className="relative flex size-10 items-center justify-center rounded-full border border-neutral-100 text-neutral-700 hover:bg-neutral-25"
      >
        <Bell className="size-[18px]" aria-hidden="true" />
        {count > 0 && (
          <span aria-hidden="true" className="num absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-500 px-1 text-caption font-bold text-neutral-0">
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>
      {open && <NotificationPanel hasUnread={count > 0} push={push} onClose={() => setOpen(false)} />}
    </div>
  );
}

function NotificationPanel({ hasUnread, push, onClose }: { hasUnread: boolean; push: ReturnType<typeof useWebPush>; onClose: () => void }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const query = useInfiniteQuery({
    queryKey: [...NOTIFICATIONS_KEY, 'list'],
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/notifications', { params: { query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const notifications = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  const refresh = () => void queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY });
  const read = useMutation({
    mutationFn: (id: number) => call(api.POST('/api/v1/notifications/{notificationId}/read', { params: { path: { notificationId: id } } })),
    onSettled: refresh,
  });
  const readAll = useMutation({
    mutationFn: () => call(api.POST('/api/v1/notifications/read-all')),
    onSettled: refresh,
  });

  const open = (notification: Notification) => {
    if (notification.id !== undefined && !notification.read) read.mutate(notification.id);
    onClose();
    const link = notificationLink(notification);
    // 방송을 보는 중이었으면 방송은 작은 플레이어에서 이어진다. (MiniPlayer)
    if (link) navigate(link);
  };

  return (
    <div role="dialog" aria-label="알림" className="absolute top-12 right-0 z-50 flex w-[360px] flex-col rounded-lg border border-neutral-100 bg-neutral-0 shadow-md">
      <div className="flex items-center justify-between border-b border-neutral-100 px-4 py-3">
        <strong className="text-body font-bold">알림</strong>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="sm" disabled={!hasUnread} loading={readAll.isPending} onClick={() => readAll.mutate()}>
            모두 읽음
          </Button>
          <Link to="/me/notifications" onClick={onClose} aria-label="알림 설정" title="알림 설정" className="flex size-8 items-center justify-center rounded-md text-neutral-500 hover:bg-neutral-50">
            <Settings className="size-4" aria-hidden="true" />
          </Link>
        </div>
      </div>
      <BrowserPushSetting push={push} className="border-b border-neutral-100 px-4 py-3" />
      <div className="max-h-[420px] overflow-y-auto">
        {query.isPending ? (
          <div className="flex flex-col gap-2 p-4">
            <Skeleton className="h-14" />
            <Skeleton className="h-14" />
          </div>
        ) : query.isError && notifications.length === 0 ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : notifications.length === 0 ? (
          <EmptyState title="알림이 없어요" description="판매자를 구독하거나 경매에서 [알림 받기]를 누르면 방송 시작·경매 소식을 여기로 알려 드려요." />
        ) : (
          <ul aria-label="알림 목록" className="divide-y divide-neutral-100">
            {notifications.map((notification) => (
              <li key={notification.id}>
                <button
                  type="button"
                  onClick={() => open(notification)}
                  className={cn('flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-neutral-25', !notification.read && 'bg-info-50/60')}
                >
                  <NotificationIcon type={notification.type} />
                  <span className="flex min-w-0 flex-col gap-0.5">
                    <span className="text-body font-semibold">{notificationTitle(notification)}</span>
                    {notification.body && <span className="line-clamp-2 text-label text-neutral-700">{notification.body}</span>}
                    <span className="text-caption text-neutral-500">
                      {notification.createdAt ? formatDateTime(notification.createdAt) : ''}
                      {!notification.read && ' · 안 읽음'}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
        <div ref={sentinelRef} />
        {query.isFetchingNextPage && <Skeleton className="m-4 h-14" />}
      </div>
      <Link to="/me/auction-alerts" onClick={onClose} className="flex items-center justify-center gap-1.5 border-t border-neutral-100 px-4 py-2.5 text-label font-semibold text-neutral-700 hover:bg-neutral-25">
        <Gavel className="size-4" aria-hidden="true" />
        알림 받는 경매 보기
      </Link>
    </div>
  );
}
