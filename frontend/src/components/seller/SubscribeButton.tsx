import { Bell, BellOff } from 'lucide-react';
import { errorMessage, useSession } from '@/api';
import { useLoginGuard } from '@/components/auth/use-login-guard';
import { Button, Notice } from '@/components/ui';
import { useSubscription } from '@/lib/use-seller';

/**
 * 판매자 구독·구독 취소. 구독하면 그 판매자가 방송을 시작할 때 알림이 온다.
 * 로그인하지 않았으면 로그인 창을 띄우고, 로그인하면 이어서 구독한다. 내 프로필에서는 그리지 않는다.
 */
export function SubscribeButton({ sellerId, subscribed, size = 'sm' }: { sellerId: number; subscribed: boolean; size?: 'sm' | 'md' }) {
  const session = useSession();
  const { guard, loginDialog } = useLoginGuard();
  const subscription = useSubscription(sellerId);

  if (session?.user.userId === sellerId) return null;

  return (
    <div className="flex flex-col items-start gap-1.5">
      <Button
        size={size}
        variant={subscribed ? 'outline' : 'primary'}
        loading={subscription.isPending}
        onClick={() => guard(() => subscription.mutate(!subscribed))}
      >
        {subscribed ? <BellOff className="size-4" aria-hidden="true" /> : <Bell className="size-4" aria-hidden="true" />}
        {subscribed ? '구독 취소' : '구독'}
      </Button>
      {subscription.isError && <Notice tone="error">{errorMessage(subscription.error)}</Notice>}
      {loginDialog}
    </div>
  );
}
