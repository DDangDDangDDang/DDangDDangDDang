import { useInfiniteQuery } from '@tanstack/react-query';
import { Building2, CalendarDays, Clock, Gavel, Gift, PackageCheck, Radio, ShieldCheck, Star, Truck } from 'lucide-react';
import { useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, type Schema } from '@/api';
import { AuctionPhaseBadge } from '@/components/auction/AuctionBits';
import { PowerSellerBadge } from '@/components/donation/PowerSellerBadge';
import { Badge, buttonVariants, Dialog, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { auctionPhase } from '@/lib/auction';
import { cn } from '@/lib/cn';
import { formatDate, formatDateTime, formatDotDate, formatMileage, formatNumber, formatPoint } from '@/lib/format';
import {
  formatBroadcastDuration,
  formatShippingTime,
  SELLER_AUCTION_TABS,
  sellerAuctionEmpty,
  sellerAuctionPriceLabel,
  sellerAuctionQuery,
  soldRate,
  type SellerAuctionTab,
} from '@/lib/seller';
import { satisfactionLabel } from '@/lib/review';
import { useInfiniteScroll } from '@/lib/use-infinite-scroll';
import { useNow } from '@/lib/use-now';
import { useSellerProfile } from '@/lib/use-seller';
import { SellerAvatar } from './SellerAvatar';
import { SellerReviewsTab } from './SellerReviewsTab';
import { SubscribeButton } from './SubscribeButton';

type Profile = Schema<'SellerProfileResponse'>;
type Tab = 'info' | 'auctions' | 'reviews';

const TABS: { key: Tab; label: string }[] = [
  { key: 'info', label: '기본 정보' },
  { key: 'auctions', label: '경매 기록' },
  { key: 'reviews', label: '리뷰' },
];

/** 경매 기록을 한 번에 불러오는 수 */
const AUCTION_PAGE_SIZE = 10;

/**
 * 판매자 프로필 창. 기본 탭(인증·구독자·방송 기록·신뢰 지표), 경매 기록 탭, 리뷰 탭(둘 다 10개씩 이어서 불러오기).
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
            {TABS.map((item) => (
              <TabButton key={item.key} selected={tab === item.key} onClick={() => setTab(item.key)}>
                {item.label}
              </TabButton>
            ))}
          </div>
          <div role="tabpanel" aria-label={TABS.find((item) => item.key === tab)?.label}>
            {tab === 'info' && <InfoTab profile={profile} />}
            {tab === 'auctions' && <AuctionsTab sellerId={sellerId} onNavigate={onClose} />}
            {tab === 'reviews' && <SellerReviewsTab sellerId={sellerId} trust={profile.trust} />}
          </div>
        </div>
      ) : null}
    </Dialog>
  );
}

function ProfileHeader({ profile, sellerId, onNavigate }: { profile: Profile; sellerId: number; onNavigate: () => void }) {
  return (
    <div className="flex items-start gap-4">
      <SellerAvatar name={profile.nickname} imageUrl={profile.profileImageUrl} verified={profile.verifiedSeller} size="lg" />
      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <strong className="text-title-md font-bold">{profile.nickname || '판매자'}</strong>
          {profile.verifiedSeller && (
            <Badge variant="info">
              <ShieldCheck className="size-3.5" aria-hidden="true" />
              판매자 인증
            </Badge>
          )}
          {/* 사업자등록번호로 인증받은 판매자는 판매자 인증 배지 옆에 따로 표시한다. */}
          {profile.businessSeller && (
            <Badge variant="outline">
              <Building2 className="size-3.5" aria-hidden="true" />
              사업자
            </Badge>
          )}
          {/* 받은 도네이션 마일리지로 파워 판매자를 산 판매자 */}
          {profile.powerSeller && <PowerSellerBadge />}
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
        {profile.bio && <p className="mt-1 text-body whitespace-pre-line text-neutral-500">{profile.bio}</p>}
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
          {/* 구독자 수는 위 머리말에 있다. 여기서는 구매 확정한 구매자들이 남긴 별점 */}
          <Stat icon={<Star className="size-4" aria-hidden="true" />} label="구매자 만족도" value={satisfactionLabel(trust)} hint="구매 확정한 구매자의 리뷰" />
          <Stat icon={<PackageCheck className="size-4" aria-hidden="true" />} label="거래 완료" value={`${formatNumber(trust?.completedOrderCount ?? 0)}건`} hint="구매자가 구매 확정한 거래" />
          <Stat
            icon={<Gavel className="size-4" aria-hidden="true" />}
            label="낙찰률"
            value={rate === undefined ? '-' : `${rate}%`}
            hint={`낙찰 ${formatNumber(trust?.soldAuctionCount ?? 0)} · 유찰 ${formatNumber(trust?.failedAuctionCount ?? 0)} · 취소 ${formatNumber(trust?.canceledAuctionCount ?? 0)}`}
          />
          <Stat icon={<Truck className="size-4" aria-hidden="true" />} label="평균 발송" value={shipping ?? '-'} hint="낙찰 뒤 발송까지" />
          <Stat
            icon={<ShieldCheck className="size-4" aria-hidden="true" />}
            label="판매자 인증"
            value={profile.verifiedSeller ? (profile.verifiedAt ? `${formatDotDate(profile.verifiedAt)} 인증` : '인증') : '인증 전'}
            hint={profile.joinedAt ? `${formatDate(profile.joinedAt)} 가입` : undefined}
          />
          {/* 시청자가 방송에서 도네이션 마일리지로 응원한 기록. 구매 확정한 회원만 마일리지가 있어, 실제로 거래한 사람들의 응원이다. */}
          <Stat
            icon={<Gift className="size-4" aria-hidden="true" />}
            label="응원"
            value={`${formatNumber(trust?.donorCount ?? 0)}명`}
            hint={`최근 ${formatNumber(trust?.donationWindowDays ?? 0)}일 · ${formatMileage(trust?.receivedDonationPoints ?? 0)} 받음`}
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

/**
 * 경매 기록: 진행 중(기본) / 시작 전 / 지난 경매 탭. 지난 경매는 낙찰만 보이고, "유찰·취소 포함"을 켜면 유찰·취소도 보인다.
 * 탭이나 체크를 바꾸면 목록을 처음부터 다시 불러온다. (조건마다 따로 불러오고, 스크롤도 맨 위로)
 */
function AuctionsTab({ sellerId, onNavigate }: { sellerId: number; onNavigate: () => void }) {
  const [tab, setTab] = useState<SellerAuctionTab>('ONGOING');
  const [includeFailedAndCanceled, setIncludeFailedAndCanceled] = useState(false);
  const tabRefs = useRef<Partial<Record<SellerAuctionTab, HTMLButtonElement | null>>>({});
  // 탭 패턴: 좌우 화살표·Home·End로 탭을 옮기고, 옮긴 탭에 포커스를 준다.
  const moveTab = (event: KeyboardEvent<HTMLButtonElement>) => {
    const index = SELLER_AUCTION_TABS.findIndex((item) => item.key === tab);
    const last = SELLER_AUCTION_TABS.length - 1;
    const next = { ArrowRight: index === last ? 0 : index + 1, ArrowLeft: index === 0 ? last : index - 1, Home: 0, End: last }[event.key];
    const target = next === undefined ? undefined : SELLER_AUCTION_TABS[next];
    if (!target) return;
    event.preventDefault();
    setTab(target.key);
    tabRefs.current[target.key]?.focus();
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <div role="tablist" aria-label="경매 상태" className="flex gap-1 rounded-md bg-neutral-50 p-1">
          {SELLER_AUCTION_TABS.map((item) => (
            <button
              key={item.key}
              ref={(el) => {
                tabRefs.current[item.key] = el;
              }}
              id={`seller-auction-tab-${item.key}`}
              type="button"
              role="tab"
              aria-selected={tab === item.key}
              aria-controls="seller-auction-tabpanel"
              tabIndex={tab === item.key ? 0 : -1}
              onClick={() => setTab(item.key)}
              onKeyDown={moveTab}
              className={cn(
                'h-8 rounded-sm px-3 text-label font-semibold transition-colors',
                tab === item.key ? 'bg-neutral-0 text-neutral-900 shadow-sm' : 'text-neutral-500 hover:text-neutral-900',
              )}
            >
              {item.label}
            </button>
          ))}
        </div>
        {tab === 'PAST' && (
          <label className="flex items-center gap-2 text-label text-neutral-700">
            <input
              type="checkbox"
              checked={includeFailedAndCanceled}
              onChange={(e) => setIncludeFailedAndCanceled(e.target.checked)}
              className="size-4 accent-brand-500"
            />
            유찰·취소 포함
          </label>
        )}
      </div>
      <div id="seller-auction-tabpanel" role="tabpanel" aria-labelledby={`seller-auction-tab-${tab}`}>
        <AuctionList
          key={`${tab}-${includeFailedAndCanceled}`}
          sellerId={sellerId}
          tab={tab}
          includeFailedAndCanceled={includeFailedAndCanceled}
          onNavigate={onNavigate}
        />
      </div>
    </div>
  );
}

/** 탭 하나의 경매 목록: 최근 경매부터 10개씩. 끝까지 내리면 다음 10개를 불러온다. */
function AuctionList({
  sellerId,
  tab,
  includeFailedAndCanceled,
  onNavigate,
}: {
  sellerId: number;
  tab: SellerAuctionTab;
  includeFailedAndCanceled: boolean;
  onNavigate: () => void;
}) {
  const now = useNow(null);
  const filter = sellerAuctionQuery(tab, includeFailedAndCanceled);
  const query = useInfiniteQuery({
    queryKey: ['seller-auctions', sellerId, filter],
    // 탭을 떠나면 캐시를 바로 지운다. 다시 돌아왔을 때 예전에 내려 본 여러 페이지가 잠깐 보이지 않고 첫 페이지부터 불러오게.
    gcTime: 0,
    queryFn: ({ pageParam }) =>
      call(api.GET('/api/v1/sellers/{sellerId}/auctions', { params: { path: { sellerId }, query: { ...filter, cursor: pageParam, size: AUCTION_PAGE_SIZE } } })),
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
  if (auctions.length === 0) return <EmptyState {...sellerAuctionEmpty(tab, includeFailedAndCanceled)} />;

  return (
    <div className="flex max-h-[420px] flex-col overflow-y-auto">
      <ul aria-label="경매 기록" className="divide-y divide-neutral-100">
        {auctions.map((auction) => {
          const phase = auctionPhase(auction, now);
          const priceLabel = sellerAuctionPriceLabel(auction.status);
          // 고른 가격이 응답에 없으면 0 P처럼 보이지 않게 -로 둔다.
          const price = priceLabel === '시작가' ? auction.startPrice : auction.currentPrice;
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
                  <span className="text-caption text-neutral-500">{priceLabel}</span>
                  <span className="num text-body font-bold">
                    {price === undefined ? '-' : formatPoint(price)}
                  </span>
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
