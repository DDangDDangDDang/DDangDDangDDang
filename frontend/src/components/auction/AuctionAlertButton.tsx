import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, BellRing } from 'lucide-react';
import { api, call, errorMessage, useSession } from '@/api';
import { Button, Notice } from '@/components/ui';
import { useWebPush } from '@/lib/web-push';

const auctionAlertKey = (auctionId: number) => ['auction-alert', auctionId] as const;

/**
 * 경매 알림 받기. 켜 두면 경매 시작·마감 10분 전·취소·종료를 알림함(+브라우저 알림)으로 알려 준다.
 * 입찰한 경매는 켜지 않아도 상위 입찰·마감 임박·취소·종료 알림이 온다.
 * 로그인하지 않았으면 requireLogin으로 로그인 창을 띄운다.
 * 알림을 받기로 했는데 브라우저 알림이 꺼져 있으면, 탭을 닫아도 받을 수 있게 바로 켜자고 안내한다.
 */
export function AuctionAlertButton({ auctionId, requireLogin }: { auctionId: number; requireLogin: (then: () => void) => void }) {
  const session = useSession();
  const queryClient = useQueryClient();
  const alert = useQuery({
    queryKey: auctionAlertKey(auctionId),
    queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}/alert', { params: { path: { auctionId } } })),
    enabled: Boolean(session),
  });
  const toggle = useMutation({
    mutationFn: (subscribe: boolean) =>
      subscribe
        ? call(api.PUT('/api/v1/auctions/{auctionId}/alert', { params: { path: { auctionId } } }))
        : call(api.DELETE('/api/v1/auctions/{auctionId}/alert', { params: { path: { auctionId } } })),
    onSuccess: (data) => queryClient.setQueryData(auctionAlertKey(auctionId), data),
  });
  const subscribed = Boolean(session) && alert.data?.subscribed === true;
  const push = useWebPush(session?.user.userId);

  return (
    <>
      <Button
        variant="outline"
        aria-pressed={subscribed}
        loading={toggle.isPending}
        disabled={Boolean(session) && alert.isPending}
        onClick={() => requireLogin(() => toggle.mutate(!subscribed))}
      >
        {subscribed ? <BellRing className="size-4 text-brand-500" aria-hidden="true" /> : <Bell className="size-4" aria-hidden="true" />}
        {subscribed ? '알림 받는 중' : '알림 받기'}
      </Button>
      {subscribed && <span className="text-caption text-neutral-500">시작·마감 10분 전·취소·결과를 알려 드려요.</span>}
      {subscribed && push.state === 'off' && (
        <div className="flex items-center justify-between gap-3 rounded-md bg-info-50 px-3 py-2.5">
          <span className="text-label text-neutral-700">지금은 사이트를 열어 둘 때만 알려 드려요. 탭을 닫아도 받으려면 브라우저 알림을 켜 주세요.</span>
          <Button size="sm" className="shrink-0" loading={push.pending} onClick={() => void push.enable()}>
            브라우저 알림 켜기
          </Button>
        </div>
      )}
      {subscribed && push.state === 'denied' && (
        <Notice tone="info">이 사이트의 브라우저 알림이 차단돼 있어 사이트를 열어 둘 때만 알려 드려요. 주소창 왼쪽 아이콘 → 알림을 허용으로 바꿔 주세요.</Notice>
      )}
      {subscribed && push.error && <Notice tone="error">{push.error}</Notice>}
      {toggle.isError && <Notice tone="error">{errorMessage(toggle.error)}</Notice>}
    </>
  );
}
