import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Eye, Volume2, VolumeX } from "lucide-react";
import { useState, type ReactNode } from "react";
import { Link } from "react-router";
import { api, call, errorMessage, type Schema } from "@/api";
import { AdBanner } from "@/components/ad/AdBanner";
import {
  AuctionCard,
  AuctionCardSkeleton,
} from "@/components/auction/AuctionBits";
import { Hashtags } from "@/components/live/Hashtags";
import {
  LiveStreamCard,
  LiveStreamCardSkeleton,
  LiveThumbnail,
} from "@/components/live/LiveStreamCard";
import { PreviewVideo } from "@/components/live/PreviewVideo";
import { RecordedBadge } from "@/components/live/RecordedBadge";
import { useStreamPreview } from "@/components/live/use-live-preview";
import { TrackAudio } from "@/components/watch/tracks";
import { SellerAvatar } from "@/components/seller/SellerAvatar";
import {
  Badge,
  buttonVariants,
  EmptyState,
  ErrorState,
  Skeleton,
} from "@/components/ui";
import { formatDateTime, formatNumber } from "@/lib/format";
import { useHoverIntent } from "@/lib/use-hover-intent";
import { usePrefersReducedMotion } from "@/lib/use-reduced-motion";

type Card = Schema<"LiveStreamCard">;

/** 섹션별로 보여줄 방송 수 (한 줄 4개씩 2줄) */
const SECTION_SIZE = 8;

/**
 * 홈: 대표 라이브 · 배너 광고 · 인기 라이브 · 시청자 급상승 · 방송 예정 · 인기 경매.
 * 로그인하지 않아도 방송·경매를 둘러볼 수 있다. (로그인·회원가입은 헤더 오른쪽 위)
 */
export function HomePage() {
  return (
    <div className="flex flex-col gap-12">
      <LiveHome />
      <PopularAuctions />
    </div>
  );
}

function LiveHome() {
  const query = useQuery({
    queryKey: ["live-streams", "home", SECTION_SIZE],
    queryFn: () =>
      call(
        api.GET("/api/v1/live-streams/home", {
          params: { query: { size: SECTION_SIZE } },
        }),
      ),
  });

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-12">
        <Skeleton className="aspect-[21/8] rounded-lg" />
        <AdBanner placement="home-top" />
        <Section title="지금 인기 라이브">
          <CardGrid>
            {Array.from({ length: 4 }, (_, i) => (
              <LiveStreamCardSkeleton key={i} />
            ))}
          </CardGrid>
        </Section>
      </div>
    );
  }

  if (query.isError) {
    return (
      <ErrorState
        message={errorMessage(query.error)}
        onRetry={() => void query.refetch()}
      />
    );
  }

  const mostViewed = query.data?.mostViewed ?? [];
  const surging = query.data?.surging ?? [];
  const upcoming = query.data?.upcoming ?? [];
  const [featured, ...others] = mostViewed;

  return (
    <div className="flex flex-col gap-12">
      {featured ? (
        <FeaturedLive card={featured} others={others.slice(0, 5)} />
      ) : (
        <div className="rounded-lg border border-neutral-100 bg-neutral-0">
          <EmptyState
            title="지금은 라이브 중인 방송이 없어요"
            description="판매자가 방송을 시작하면 여기에 크게 나와요. 아래 예정된 방송과 경매를 둘러보세요."
            action={
              <Link
                to="/lives?status=ENDED"
                className={buttonVariants({ variant: "outline", size: "sm" })}
              >
                지난 방송 보기
              </Link>
            }
          />
        </div>
      )}

      <AdBanner placement="home-top" />

      {mostViewed.length > 0 && (
        <Section title="지금 인기 라이브" moreTo="/lives">
          <CardGrid>
            {mostViewed.map((card) => (
              <LiveStreamCard key={card.liveStreamId} card={card} />
            ))}
          </CardGrid>
        </Section>
      )}

      {surging.length > 0 && (
        <Section
          title="시청자가 몰리는 방송"
          description="최근 시청자가 빠르게 늘고 있어요."
        >
          <CardGrid>
            {surging.map((card) => (
              <LiveStreamCard
                key={card.liveStreamId}
                card={card}
                showIncrease
              />
            ))}
          </CardGrid>
        </Section>
      )}

      {upcoming.length > 0 && (
        <Section title="방송 예정" moreTo="/lives?status=SCHEDULED">
          <ul className="grid grid-cols-3 gap-4">
            {upcoming.map((card) => (
              <li key={card.liveStreamId}>
                <UpcomingCard card={card} />
              </li>
            ))}
          </ul>
        </Section>
      )}
    </div>
  );
}

/**
 * 맨 위 대표 라이브: 가장 많이 보는 방송을 크게, 그다음 방송들은 아래 작은 썸네일로.
 * 치지직처럼 대표 방송은 홈에 들어오면 소리 없이 바로 미리보기를 틀고, [소리 켜기]로 소리를 들을 수 있다.
 * 운영체제에서 "동작 줄이기"를 켰으면 자동으로 틀지 않고 마우스를 올렸을 때만 보여 준다.
 */
function FeaturedLive({ card, others }: { card: Card; others: Card[] }) {
  const seller = card.sellerNickname || "판매자";
  const to = `/lives/${card.liveStreamId}`;
  const reducedMotion = usePrefersReducedMotion();
  const { hovered, onMouseEnter, onMouseLeave } = useHoverIntent();
  const [soundOn, setSoundOn] = useState(false);
  const preview = useStreamPreview(
    card.liveStreamId ?? 0,
    card.liveStreamId !== undefined && (!reducedMotion || hovered),
    soundOn,
  );
  // 작은 플레이어로 보고 있는 방송이면 소리는 작은 플레이어에서 켜고 끈다.
  const canToggleSound = !!preview.videoTrack && !preview.shared;

  const toggleSound = () => {
    // 소리를 켤 때는 사용자가 누른 순간에 재생을 허용한다.
    if (!soundOn) void preview.startAudio();
    setSoundOn(!soundOn);
  };

  return (
    <section aria-label="대표 라이브" className="flex flex-col gap-3">
      <div className="flex overflow-hidden rounded-lg border border-neutral-100 bg-neutral-0">
        <div
          className="relative min-w-0 flex-[3]"
          onMouseEnter={onMouseEnter}
          onMouseLeave={onMouseLeave}
        >
          <Link
            to={to}
            state={{ sellerNickname: card.sellerNickname }}
            aria-label={`${card.title || "제목 없는 방송"} 방송 보기`}
            className="block"
          >
            <LiveThumbnail name={seller} className="rounded-none">
              <PreviewVideo track={preview.videoTrack} />
              <div className="absolute top-4 left-4 flex gap-1.5">
                <Badge variant="live">LIVE</Badge>
                <Badge className="bg-stage/70 font-semibold text-white">
                  <Eye className="size-3.5" aria-hidden="true" />
                  <span className="num">
                    {formatNumber(card.viewerCount ?? 0)}
                  </span>
                  <span>명 시청</span>
                </Badge>
                {card.recorded && <RecordedBadge />}
              </div>
            </LiveThumbnail>
          </Link>
          {canToggleSound && (
            <button
              type="button"
              onClick={toggleSound}
              className="absolute right-4 bottom-4 flex items-center gap-1.5 rounded-md bg-stage/70 px-3 py-2 text-label font-semibold text-white hover:bg-stage/90"
            >
              {soundOn ? (
                <Volume2 className="size-4" aria-hidden="true" />
              ) : (
                <VolumeX className="size-4" aria-hidden="true" />
              )}
              {soundOn ? "소리 끄기" : "소리 켜기"}
            </button>
          )}
          {soundOn && preview.audioTrack && (
            <TrackAudio track={preview.audioTrack} muted={false} />
          )}
        </div>
        <div className="flex min-w-[320px] flex-1 flex-col gap-4 p-6">
          <div className="flex items-center gap-3">
            <SellerAvatar name={seller} size="md" live />
            <span className="flex min-w-0 flex-col">
              <span className="truncate font-bold">{seller}</span>
              {card.startedAt && (
                <span className="text-caption text-neutral-500">
                  {formatDateTime(card.startedAt)} 시작
                </span>
              )}
            </span>
          </div>
          <h1 className="line-clamp-3 text-title-lg font-bold tracking-tight">
            {card.title || "제목 없는 방송"}
          </h1>
          <Hashtags tags={card.hashtags} />
          <Link
            to={to}
            state={{ sellerNickname: card.sellerNickname }}
            className={buttonVariants({
              variant: "primary",
              size: "lg",
              className: "mt-auto w-full",
            })}
          >
            방송 보기
          </Link>
        </div>
      </div>
      {others.length > 0 && (
        <ul aria-label="다른 인기 방송" className="grid grid-cols-5 gap-3">
          {others.map((other) => (
            <li key={other.liveStreamId}>
              <Link
                to={`/lives/${other.liveStreamId}`}
                state={{ sellerNickname: other.sellerNickname }}
                className="group flex flex-col gap-1.5"
              >
                <LiveThumbnail
                  name={other.sellerNickname || "판매자"}
                  className="rounded-md opacity-80 transition-opacity group-hover:opacity-100"
                />
                <span className="truncate text-label font-semibold text-neutral-700 group-hover:text-neutral-900">
                  {other.title || "제목 없는 방송"}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** 인기 경매: 진행 중인 경매 중 입찰이 많은 순. 방송 섹션과 따로 불러와 한쪽이 실패해도 다른 쪽은 보인다. */
function PopularAuctions() {
  const query = useQuery({
    queryKey: ["auctions", "popular", 4],
    queryFn: async () =>
      (await call(
        api.GET("/api/v1/auctions/popular", { params: { query: { size: 4 } } }),
      )) ?? [],
  });

  return (
    <Section
      title="인기 경매"
      description="방송을 보지 않아도 바로 입찰할 수 있어요."
      // 홈 인기 경매와 같은 기준(입찰 수 많은 순)으로 연다. 왼쪽 [경매] 메뉴로 들어가면 최신순 그대로.
      moreTo="/auctions?status=ONGOING&sort=POPULAR"
    >
      {query.isPending ? (
        <CardGrid>
          {Array.from({ length: 4 }, (_, i) => (
            <AuctionCardSkeleton key={i} />
          ))}
        </CardGrid>
      ) : query.isError ? (
        <ErrorState
          message={errorMessage(query.error)}
          onRetry={() => void query.refetch()}
        />
      ) : query.data.length === 0 ? (
        <p className="rounded-lg border border-dashed border-neutral-200 px-5 py-8 text-center text-body text-neutral-500">
          지금 진행 중인 경매가 없어요. 판매자가 경매를 열면 여기에 나와요.
        </p>
      ) : (
        <CardGrid>
          {query.data.map((auction) => (
            <AuctionCard key={auction.auctionId} auction={auction} />
          ))}
        </CardGrid>
      )}
    </Section>
  );
}

function Section({
  title,
  description,
  moreTo,
  children,
}: {
  title: string;
  description?: string;
  moreTo?: string;
  children: ReactNode;
}) {
  return (
    <section aria-label={title} className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3">
        <div className="flex items-baseline gap-3">
          <h2 className="text-title-lg font-bold tracking-tight">{title}</h2>
          {description && (
            <p className="text-body text-neutral-500">{description}</p>
          )}
        </div>
        {moreTo && (
          <Link
            to={moreTo}
            className="flex items-center gap-0.5 text-label font-semibold text-neutral-500 hover:text-neutral-900"
          >
            전체 보기
            <ChevronRight className="size-4" aria-hidden="true" />
          </Link>
        )}
      </div>
      {children}
    </section>
  );
}

function CardGrid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-4 gap-x-5 gap-y-8">{children}</div>;
}

/** 방송 예정: 시작 시각을 왼쪽 네모 안에 크게 */
function UpcomingCard({ card }: { card: Card }) {
  return (
    <Link
      to={`/lives/${card.liveStreamId}`}
      state={{ sellerNickname: card.sellerNickname }}
      className="flex items-center gap-4 rounded-lg border border-neutral-100 bg-neutral-0 p-4 hover:border-neutral-200 hover:bg-neutral-50"
    >
      <span className="flex w-[104px] shrink-0 flex-col items-center justify-center rounded-md bg-neutral-50 px-2 py-2.5 text-center text-label font-bold text-brand-700">
        {card.scheduledStartAt
          ? formatDateTime(card.scheduledStartAt)
          : "시작 시간 미정"}
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <strong className="truncate text-body-lg font-semibold">
          {card.title || "제목 없는 방송"}
        </strong>
        <span className="text-label text-neutral-500">
          {card.sellerNickname || "판매자"}
        </span>
      </span>
    </Link>
  );
}
