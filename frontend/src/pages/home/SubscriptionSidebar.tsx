import { useQuery } from '@tanstack/react-query';
import { Users } from 'lucide-react';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { SellerAvatar } from '@/components/seller/SellerAvatar';
import { useOpenSellerProfile } from '@/components/seller/seller-profile-context';
import { Badge, buttonVariants, ErrorState, Skeleton } from '@/components/ui';
import { MY_SUBSCRIPTIONS_KEY } from '@/lib/use-seller';

type Subscribed = Schema<'SubscribedSellerResponse'>;

/**
 * 홈 왼쪽: 내가 구독 중인 판매자. 방송 중인 판매자가 위에 오고 이름 옆에 LIVE가 붙는다. LIVE를 누르면 그 방송으로 간다.
 * 인증 판매자는 이름을 누르면 판매자 프로필 창이 열린다. 로그인하지 않았으면 로그인 안내를 보여 준다.
 */
export function SubscriptionSidebar() {
  const session = useSession();
  return (
    <aside aria-labelledby="subscriptions-title" className="sticky top-6 flex w-[240px] shrink-0 flex-col gap-3 self-start rounded-lg border border-neutral-100 bg-neutral-0 p-4 shadow-sm">
      <h2 id="subscriptions-title" className="flex items-center gap-2 text-body-lg font-bold">
        <Users className="size-4 text-neutral-500" aria-hidden="true" />
        구독 중인 판매자
      </h2>
      {session ? (
        <SubscriptionList />
      ) : (
        <div className="flex flex-col gap-3">
          <p className="text-label text-neutral-500">로그인하고 판매자를 구독하면 방송을 시작할 때 알려 드려요.</p>
          <Link to="/login?redirect=%2F" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            로그인
          </Link>
        </div>
      )}
    </aside>
  );
}

function SubscriptionList() {
  const query = useQuery({
    queryKey: MY_SUBSCRIPTIONS_KEY,
    queryFn: () => call(api.GET('/api/v1/users/me/subscriptions')),
    // 구독한 판매자의 방송 시작은 알림으로 바로 반영되고(DmRealtime), 끝난 방송은 1분마다 다시 확인한다.
    refetchInterval: 60_000,
  });

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-9" />
        ))}
      </div>
    );
  }
  if (query.isError) return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;

  const sellers = query.data ?? [];
  if (sellers.length === 0) {
    return <p className="text-label text-neutral-500">아직 구독한 판매자가 없어요. 방송 화면에서 [구독]을 누르면 여기에 모여요.</p>;
  }

  return (
    <ul aria-label="구독 중인 판매자" className="flex flex-col gap-1">
      {sellers.map((seller) => (
        <li key={seller.sellerId}>
          <SubscribedRow seller={seller} />
        </li>
      ))}
    </ul>
  );
}

function SubscribedRow({ seller }: { seller: Subscribed }) {
  const openProfile = useOpenSellerProfile();
  const name = seller.nickname || '판매자';
  const identity = (
    <>
      <SellerAvatar name={name} verified={seller.verifiedSeller} size="sm" />
      <span className="truncate text-body font-semibold">{name}</span>
    </>
  );

  return (
    <div className="flex items-center justify-between gap-2 rounded-md px-1.5 py-1.5 hover:bg-neutral-25">
      {seller.verifiedSeller && seller.sellerId !== undefined ? (
        <button type="button" aria-label={`${name} 판매자 프로필 보기`} onClick={() => openProfile(seller.sellerId ?? 0)} className="flex min-w-0 items-center gap-2.5 text-left">
          {identity}
        </button>
      ) : (
        <span className="flex min-w-0 items-center gap-2.5">{identity}</span>
      )}
      {seller.liveStreamId !== undefined && (
        <Link to={`/lives/${seller.liveStreamId}`} aria-label={`${name} 방송 보러 가기${seller.liveTitle ? `: ${seller.liveTitle}` : ''}`} title={seller.liveTitle}>
          <Badge variant="live" className="shrink-0">
            <span aria-hidden="true" className="size-1.5 rounded-full bg-neutral-0" />
            LIVE
          </Badge>
        </Link>
      )}
    </div>
  );
}
