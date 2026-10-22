import { useInfiniteQuery } from '@tanstack/react-query';
import { CalendarDays, Clock, Gavel, PackageCheck, Phone, Radio, ShieldCheck, Truck, Users } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, type Schema } from '@/api';
import { AuctionPhaseBadge } from '@/components/auction/AuctionBits';
import { Badge, buttonVariants, Dialog, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { auctionPhase } from '@/lib/auction';
import { cn } from '@/lib/cn';
import { formatDate, formatDateTime, formatNumber, formatPoint } from '@/lib/format';
import { formatBroadcastDuration, formatShippingTime, soldRate } from '@/lib/seller';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';
import { useNow } from '@/lib/use-now';
import { useSellerProfile } from '@/lib/use-seller';
import { SellerAvatar } from './SellerAvatar';
import { SubscribeButton } from './SubscribeButton';

type Profile = Schema<'SellerProfileResponse'>;
type Tab = 'info' | 'auctions';

/** 경매 기록을 한 번에 불러오는 수 */
const AUCTION_PAGE_SIZE = 10;

/**
 * 판매자 프로필 창. 기본 탭(인증·구독자·방송 기록·신뢰 지표)과 경매 기록 탭(10개씩 이어서 불러오기).
 * 창 안에서 구독·구독 취소할 수 있고, 방송 중이면 방송으로 바로 간다.
 */
export function SellerProfileDialog({ sellerId, onClose }: { sellerId?: number; onClose: () => void }) {
  const [tab, setTab] = useState<Tab>('info');
  const query = useSellerProfile(sellerId);
  const profile = query.data;

  return (
    <Dialog open={sellerId !== undefined} title="판매자 프로필" onClose={onClose} className="w-[640px]">
      {query.isPending ? (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-20" />
          <Skeleton className="h-40" />
        </div>
      ) : query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : profile && sellerId !== undefined ? (
        <div className="flex flex-col gap-5">
          <ProfileHeader profile={profile} sellerId={sellerId} onNavigate={onClose} />
          <div role="tablist" aria-label="판매자 프로필" className="flex gap-1 border-b border-neutral-100">
            <TabButton selected={tab === 'info'} onClick={() => setTab('info')}>
              기본 정보
            </TabButton>
            <TabButton selected={tab === 'auctions'} onClick={() => setTab('auctions')}>
              경매 기록
            </TabButton>
          </div>
          <div role="tabpanel" aria-label={tab === 'info' ? '기본 정보' : '경매 기록'}>
            {tab === 'info' ? <InfoTab profile={profile} /> : <AuctionsTab sellerId={sellerId} onNavigate={onClose} />}
          </div>
        </div>
      ) : null}
    </Dialog>
  );
}

function ProfileHeader({ profile, sellerId, onNavigate }: { profile: Profile; sellerId: number; onNavigate: () => void }) {
  return (
    <div className="flex items-start gap-4">
      <SellerAvatar name={profile.nickname} verified={profile.verifiedSeller} size="lg" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <strong className="text-title-md font-bold">{profile.nickname || '판매자'}</strong>
          {profile.verifiedSeller && (
            <Badge variant="info">
              <ShieldCheck className="size-3.5" aria-hidden="true" />
              판매자 인증
            </Badge>
          )}
          {profile.liveStreamId ? (
            <Badge variant="live">
              <span aria-hidden="true" className="size-1.5 rounded-full bg-neutral-0" />
              LIVE
            </Badge>
          ) : null}
        </div>
        {profile.storeName && <span className="text-body font-semibold text-neutral-700">{profile.storeName}</span>}
        <span className="text-label text-neutral-500">
          구독자 <span className="num font-semibold text-neutral-900">{formatNumber(profile.subscriberCount ?? 0)}</span>명
        </span>
        {profile.introduction && <p className="mt-1 text-body whitespace-pre-line text-neutral-700">{profile.introduction}</p>}
      </div>
      <div className="flex shrink-0 flex-col items-end gap-2">
        <SubscribeButton sellerId={sellerId} subscribed={profile.subscribed ?? false} />
        {profile.liveStreamId ? (
          <Link to={`/lives/${profile.liveStreamId}`} onClick={onNavigate} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            <Radio className="size-4" aria-hidden="true" />
            방송 보러 가기
          </Link>
        ) : null}
      </div>
    </div>
  );
}

function TabButton({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={selected}
      onClick={onClick}
      className={cn(
        '-mb-px border-b-2 px-4 py-2.5 text-body font-semibold transition-colors',
        selected ? 'border-neutral-900 text-neutral-900' : 'border-transparent text-neutral-500 hover:text-neutral-900',
      )}
    >
      {children}
    </button>
  );
}

/** 기본 정보: 방송 기록 + 판매자를 믿을 수 있는지 판단할 자료 */
function InfoTab({ profile }: { profile: Profile }) {
  const trust = profile.trust;
  const rate = soldRate(trust);
  const shipping = formatShippingTime(trust?.averageShippingHours);

  return (
    <div className="flex flex-col gap-5">
      <section aria-labelledby="profile-broadcast" className="flex flex-col gap-2">
        <h3 id="profile-broadcast" className="text-body font-bold">
          방송
        </h3>
        <dl className="grid grid-cols-3 gap-2">
          <Stat icon={<CalendarDays className="size-4" aria-hidden="true" />} label="첫 방송" value={profile.firstBroadcastAt ? formatDate(profile.firstBroadcastAt) : '아직 없음'} />
          <Stat icon={<Clock className="size-4" aria-hidden="true" />} label="방송한 시간" value={formatBroadcastDuration(profile.totalBroadcastSeconds ?? 0)} />
          <Stat icon={<Radio className="size-4" aria-hidden="true" />} label="방송 횟수" value={`${formatNumber(profile.broadcastCount ?? 0)}회`} />
        </dl>
      </section>

      <section aria-labelledby="profile-trust" className="flex flex-col gap-2">
        <h3 id="profile-trust" className="text-body font-bold">
          거래 신뢰도
        </h3>
        <dl className="grid grid-cols-3 gap-2">
          <Stat icon={<Users className="size-4" aria-hidden="true" />} label="구독자" value={`${formatNumber(profile.subscriberCount ?? 0)}명`} />
          <Stat icon={<PackageCheck className="size-4" aria-hidden="true" />} label="거래 완료" value={`${formatNumber(trust?.completedOrderCount ?? 0)}건`} hint="구매자가 구매 확정한 거래" />
          <Stat
            icon={<Gavel className="size-4" aria-hidden="true" />}
            label="낙찰률"
            value={rate === undefined ? '-' : `${rate}%`}
            hint={`낙찰 ${formatNumber(trust?.soldAuctionCount ?? 0)} · 유찰 ${formatNumber(trust?.failedAuctionCount ?? 0)} · 취소 ${formatNumber(trust?.canceledAuctionCount ?? 0)}`}
          />
          <Stat icon={<Truck className="size-4" aria-hidden="true" />} label="평균 발송" value={shipping ?? '-'} hint="낙찰 뒤 발송까지" />
          <Stat icon={<Phone className="size-4" aria-hidden="true" />} label="본인 인증" value={trust?.phoneVerified ? '휴대폰 인증' : '안 함'} />
          <Stat
            icon={<ShieldCheck className="size-4" aria-hidden="true" />}
            label="판매자 인증"
            value={profile.verifiedSeller ? (profile.verifiedAt ? `${formatDate(profile.verifiedAt)} 인증` : '인증') : '안 함'}
            hint={profile.joinedAt ? `${formatDate(profile.joinedAt)} 가입` : undefined}
          />
        </dl>
      </section>
    </div>
  );
}

function Stat({ icon, label, value, hint }: { icon: ReactNode; label: string; value: string; hint?: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-md bg-neutral-25 px-3 py-3">
      <dt className="flex items-center gap-1.5 text-caption text-neutral-500">
        {icon}
        {label}
      </dt>
      <dd className="num text-body-lg font-bold">{value}</dd>
      {hint && <dd className="text-caption text-neutral-500">{hint}</dd>}
    </div>
  );
}

/** 경매 기록: 최근 경매부터 10개씩. 끝까지 내리면 다음 10개를 불러온다. */
function AuctionsTab({ sellerId, onNavigate }: { sellerId: number; onNavigate: () => void }) {
  const now = useNow(null);
  const query = useInfiniteQuery({
    queryKey: ['seller-auctions', sellerId],
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/sellers/{sellerId}/auctions', { params: { path: { sellerId }, query: { cursor: pageParam, size: AUCTION_PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  // 다음 페이지를 못 불러오면 자동으로 다시 요청하지 않는다. ([다시 시도]로)
  const sentinelRef = useInfiniteScroll(query.hasNextPage && !query.isError, query.isFetchingNextPage, query.fetchNextPage);
  const auctions = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-2">
        {Array.from({ length: 3 }, (_, i) => (
          <Skeleton key={i} className="h-16" />
        ))}
      </div>
    );
  }
  if (query.isError && auctions.length === 0) return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  if (auctions.length === 0) return <EmptyState title="아직 진행한 경매가 없어요" description="판매자가 경매를 열면 여기에 기록이 쌓여요." />;

  return (
    <div className="flex max-h-[420px] flex-col overflow-y-auto">
      <ul aria-label="경매 기록" className="divide-y divide-neutral-100">
        {auctions.map((auction) => {
          const phase = auctionPhase(auction, now);
          return (
            <li key={auction.auctionId}>
              <Link to={`/auctions/${auction.auctionId}`} onClick={onNavigate} className="flex items-center justify-between gap-3 px-1 py-3 hover:bg-neutral-25">
                <span className="flex min-w-0 flex-col gap-1">
                  <span className="flex min-w-0 items-center gap-1.5">
                    <AuctionPhaseBadge phase={phase} />
                    <strong className="truncate text-body font-semibold">{auction.productName || '이름 없는 상품'}</strong>
                    {auction.liveStreamId ? <Badge variant="neutral">방송 경매</Badge> : null}
                  </span>
                  <span className="text-caption text-neutral-500">
                    {auction.startAt ? formatDateTime(auction.startAt) : '-'} ~ {auction.endAt ? formatDateTime(auction.endAt) : '-'}
                  </span>
                </span>
                <span className="flex shrink-0 flex-col items-end">
                  <span className="text-caption text-neutral-500">{phase === 'ENDED' ? '낙찰가' : phase === 'READY' ? '시작가' : '현재가'}</span>
                  <span className="num text-body font-bold">{formatPoint(auction.currentPrice ?? auction.startPrice ?? 0)}</span>
                  <span className="text-caption text-neutral-500">
                    입찰 <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
                  </span>
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && <Skeleton className="my-2 h-16" />}
      {query.isError && <ErrorState message={errorMessage(query.error)} onRetry={() => void query.fetchNextPage()} />}
    </div>
  );
}
