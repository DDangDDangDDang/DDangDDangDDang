import { useMutation, useQueryClient } from '@tanstack/react-query';
import { X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { api, call, type NotificationMessage } from '@/api';
import { buttonVariants } from '@/components/ui';
import { NOTIFICATIONS_KEY, useNotificationEvent } from '@/lib/notification-events';
import { notificationActionLabel, notificationLink, notificationTitle } from '@/lib/notification-view';
import { NotificationIcon } from './NotificationIcon';

/** 팝업이 저절로 닫히기까지 (밀리초) */
const AUTO_CLOSE_MS = 10_000;
/** 한 번에 보여 줄 팝업 수. 더 오면 오래된 것부터 닫는다. (알림함에는 남아 있음) */
const MAX_TOASTS = 3;

/**
 * 새 알림(구독한 판매자의 방송 시작, 경매 시작·상위 입찰·마감 임박·취소·낙찰 등)이 오면 화면 오른쪽 위에 팝업을 띄운다.
 * 버튼을 누르면 알림을 읽음으로 바꾸고 그 방송·경매로 간다.
 * 알림은 알림함에도 저장돼 있어서 팝업을 닫아도 종 아이콘에서 다시 볼 수 있다.
 * (사이트 탭을 보고 있을 때는 브라우저 알림 대신 이 팝업만 뜬다. public/sw.js)
 */
export function LiveStartToasts() {
  const [toasts, setToasts] = useState<NotificationMessage[]>([]);
  const queryClient = useQueryClient();
  const read = useMutation({
    mutationFn: (id: number) => call(api.POST('/api/v1/notifications/{notificationId}/read', { params: { path: { notificationId: id } } })),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY }),
  });

  const dismiss = useCallback((id?: number) => setToasts((list) => list.filter((t) => t.id !== id)), []);

  useNotificationEvent((notification) => {
    setToasts((list) => [notification, ...list.filter((t) => t.id !== notification.id)].slice(0, MAX_TOASTS));
  });

  return (
    <div aria-live="polite" aria-label="새 알림" className="pointer-events-none fixed top-20 right-6 z-50 flex w-[340px] flex-col gap-2">
      {toasts.map((toast) => (
        <Toast
          key={toast.id}
          notification={toast}
          onDismiss={dismiss}
          onOpen={() => {
            if (toast.id !== undefined) read.mutate(toast.id);
            dismiss(toast.id);
          }}
        />
      ))}
    </div>
  );
}

function Toast({ notification, onDismiss, onOpen }: { notification: NotificationMessage; onDismiss: (id?: number) => void; onOpen: () => void }) {
  const id = notification.id;
  const link = notificationLink(notification);
  // 팝업이 뜬 때부터 한 번만 센다. (새 알림이 와서 목록이 다시 그려져도 처음부터 다시 세지 않게, 고정된 dismiss와 id만 쓴다)
  useEffect(() => {
    const timer = setTimeout(() => onDismiss(id), AUTO_CLOSE_MS);
    return () => clearTimeout(timer);
  }, [onDismiss, id]);

  return (
    <div role="status" className="pointer-events-auto flex items-start gap-3 rounded-lg border border-neutral-100 bg-neutral-0 px-4 py-3 shadow-md">
      <NotificationIcon type={notification.type} />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="text-body font-semibold">{notificationTitle(notification)}</span>
        {notification.body && <span className="line-clamp-2 text-label text-neutral-700">{notification.body}</span>}
        {link && (
          <Link to={link} onClick={onOpen} className={buttonVariants({ variant: 'primary', size: 'sm', className: 'mt-1 self-start' })}>
            {notificationActionLabel(notification)}
          </Link>
        )}
      </div>
      <button type="button" aria-label="알림 닫기" onClick={() => onDismiss(id)} className="flex size-7 shrink-0 items-center justify-center rounded-md text-neutral-500 hover:bg-neutral-50">
        <X className="size-4" aria-hidden="true" />
      </button>
    </div>
  );
}
