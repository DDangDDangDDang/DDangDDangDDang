import { useQuery } from '@tanstack/react-query';
import { Clapperboard } from 'lucide-react';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { SellerAvatar } from '@/components/seller/SellerAvatar';
import { useOpenSellerProfile } from '@/components/seller/seller-profile-context';
import { ErrorState, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { MY_SUBSCRIPTIONS_KEY } from '@/lib/use-seller';

type Subscribed = Schema<'SubscribedSellerResponse'>;

/**
 * 왼쪽 메뉴 아래: 내가 구독 중인 판매자. 방송 중인 판매자가 위에 오고, 프로필에 주황 테두리와 LIVE 점이 붙는다.
 * 방송 중이면 줄을 눌러 그 방송으로 가고, 방송 중이 아니면 판매자 프로필 창을 연다. (인증 판매자만)
 * 미리 녹화한 영상을 내보내는 방송이면 이름 아래에 녹화 영상 표시가 붙는다.
 * 메뉴를 접으면(collapsed) 프로필 사진만 보여 준다. 로그인하지 않았으면 안내 문구만 보여 준다. (로그인 버튼은 화면 오른쪽 위 하나)
 */
export function SubscriptionSidebar({ collapsed = false }: { collapsed?: boolean }) {
  const session = useSession();
  return (
    <aside aria-labelledby="subscriptions-title" className="flex flex-col gap-1">
      <h2 id="subscriptions-title" className={cn('px-3 pb-1 text-label font-bold text-neutral-500', collapsed && 'sr-only')}>
        구독 중인 판매자
      </h2>
      {session ? (
        <SubscriptionList collapsed={collapsed} />
      ) : (
        !collapsed && <p className="px-3 text-label text-neutral-500">로그인하고 판매자를 구독하면 여기에 모이고, 방송을 시작할 때 알려 드려요.</p>
      )}
    </aside>
  );
}

function SubscriptionList({ collapsed }: { collapsed: boolean }) {
  const query = useQuery({
    queryKey: MY_SUBSCRIPTIONS_KEY,
    queryFn: () => call(api.GET('/api/v1/users/me/subscriptions')),
    // 구독한 판매자의 방송 시작은 알림으로 바로 반영되고(DmRealtime), 끝난 방송은 1분마다 다시 확인한다.
    refetchInterval: 60_000,
  });

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-2 px-3">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-9" />
        ))}
      </div>
    );
  }
  if (query.isError) return collapsed ? null : <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;

  const sellers = query.data ?? [];
  if (sellers.length === 0) {
    return collapsed ? null : <p className="px-3 text-label text-neutral-500">아직 구독한 판매자가 없어요. 방송 화면에서 [구독]을 누르면 여기에 모여요.</p>;
  }

  return (
    <ul aria-label="구독 중인 판매자" className={cn('flex flex-col gap-0.5', collapsed && 'items-center gap-1.5')}>
      {sellers.map((seller) => (
        <li key={seller.sellerId}>
          <SubscribedRow seller={seller} collapsed={collapsed} />
        </li>
      ))}
    </ul>
  );
}

function SubscribedRow({ seller, collapsed }: { seller: Subscribed; collapsed: boolean }) {
  const openProfile = useOpenSellerProfile();
  const name = seller.nickname || '판매자';
  const live = Boolean(seller.liveStreamId);
  const row = cn('flex items-center gap-2.5 rounded-md text-left hover:bg-neutral-50', collapsed ? 'relative size-12 justify-center' : 'min-h-11 w-full px-3 py-1.5');

  const body = (
    <>
      <SellerAvatar name={name} verified={seller.verifiedSeller} size="sm" live={live} />
      {/* 접힌 메뉴에서도 방송 중임을 색만이 아니라 글자로 보여 준다. */}
      {collapsed && live && (
        <span aria-hidden="true" className="absolute bottom-0 left-1/2 -translate-x-1/2 rounded-sm bg-red-500 px-1 text-caption leading-none font-bold text-white">
          LIVE
        </span>
      )}
      {!collapsed && (
        <>
          <span className={cn('flex min-w-0 flex-1 flex-col', !live && 'opacity-70')}>
            <span className="truncate text-body font-semibold">{name}</span>
            {live && seller.liveRecorded ? (
              <span className="flex items-center gap-1 text-caption text-neutral-500">
                <Clapperboard className="size-3" aria-hidden="true" />
                녹화 영상
              </span>
            ) : (
              <span className="truncate text-caption text-neutral-500">{live ? seller.liveTitle || '방송 중' : '오프라인'}</span>
            )}
          </span>
          {live && (
            <span className="flex shrink-0 items-center gap-1 text-caption font-bold text-red-300">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-red-500" />
              LIVE
            </span>
          )}
        </>
      )}
    </>
  );

  if (live) {
    return (
      <Link
        to={`/lives/${seller.liveStreamId}`}
        aria-label={`LIVE ${name} ${seller.liveRecorded ? '녹화 영상 ' : ''}방송 보러 가기${seller.liveTitle ? `: ${seller.liveTitle}` : ''}`}
        title={seller.liveTitle}
        className={row}
      >
        {body}
      </Link>
    );
  }
  if (seller.verifiedSeller && seller.sellerId !== undefined) {
    return (
      <button type="button" aria-label={`${name} 판매자 프로필 보기`} onClick={() => openProfile(seller.sellerId ?? 0)} className={row}>
        {body}
      </button>
    );
  }
  return (
    <span title={name} className={row}>
      {body}
    </span>
  );
}
