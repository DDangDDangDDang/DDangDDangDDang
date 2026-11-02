import { useQuery } from "@tanstack/react-query";
import { ChevronRight, Eye, Volume2, VolumeX } from "lucide-react";
import { useRef, useState, type ReactNode } from "react";
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
import { cn } from "@/lib/cn";
import { formatDateTime, formatNumber } from "@/lib/format";
import { HomeHero } from "@/pages/home/HomeHero";
import {
  fitSideSlots,
  MIN_SIDE_SLOTS,
  sideColumnWidth,
  useHeroSize,
} from "@/pages/home/hero-layout";
import { useHoverIntent } from "@/lib/use-hover-intent";
import { usePrefersReducedMotion } from "@/lib/use-reduced-motion";
import { THUMBNAIL_INTERVAL_MS } from "@/lib/live-thumbnail";

type Card = Schema<"LiveStreamCard">;

/** 섹션별로 보여줄 방송 수 (한 줄 4개씩 2줄) */
const SECTION_SIZE = 8;

/**
 * 홈: 브랜드 배너 · 대표 라이브 · 배너 광고 · 인기 라이브 · 시청자 급상승 · 방송 예정 · 인기 경매.
 * 로그인하지 않아도 방송·경매를 둘러볼 수 있다. (로그인·회원가입은 헤더 오른쪽 위)
 */
export function HomePage() {
  return (
    <div className="flex flex-col gap-12">
      <HomeHero />
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
    // 방송 썸네일(판매자 화면 캡처)이 1분마다 바뀌므로 홈을 보고 있으면 같은 간격으로 새로 불러온다.
    refetchInterval: THUMBNAIL_INTERVAL_MS,
  });

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-12">
        <div className="grid grid-cols-[2fr_1fr] gap-3">
          <Skeleton className="aspect-video rounded-lg" />
          <div className="flex flex-col gap-3">
            <Skeleton className="flex-1 rounded-lg" />
            <Skeleton className="flex-1 rounded-lg" />
          </div>
        </div>
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
        <FeaturedLive card={featured} others={others} />
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
 * 맨 위 대표 라이브 (치지직 홈처럼): 가장 많이 보는 방송을 왼쪽에 크게, 그다음 인기 방송을 오른쪽에 작은 카드로 세로로 쌓는다.
 * 오른쪽 카드 수는 화면 크기에 맞춰 2~4개이고, 오른쪽 열 높이가 큰 화면(16:9) 높이와 같도록 열 너비를 정한다.
 * 다른 방송이 적으면 있는 만큼만 보여 준다.
 */
function FeaturedLive({ card, others }: { card: Card; others: Card[] }) {
  const ref = useRef<HTMLElement>(null);
  const { width, viewportHeight } = useHeroSize(ref);
  // 카드가 1개뿐이어도 오른쪽 열이 큰 화면만큼 커지지 않도록 최소 2칸 크기로 둔다.
  const slots = Math.min(
    fitSideSlots(width, viewportHeight),
    Math.max(MIN_SIDE_SLOTS, others.length),
  );
  const side = others.slice(0, slots);
  return (
    <section
      ref={ref}
      aria-label="대표 라이브"
      className="grid gap-3"
      style={{
        gridTemplateColumns:
          width > 0
            ? `minmax(0, 1fr) ${sideColumnWidth(width, slots)}px`
            : "minmax(0, 2fr) minmax(0, 1fr)",
      }}
    >
      <FeaturedMain card={card} />
      <div className="flex min-h-0 flex-col gap-3">
        {side.map((other) => (
          <FeaturedTile key={other.liveStreamId} card={other} />
        ))}
        {side.length === 0 && (
          <Link
            to="/lives?status=SCHEDULED"
            className="flex flex-1 flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-neutral-200 p-4 text-center text-body text-neutral-500 hover:border-neutral-300 hover:text-neutral-700"
          >
            <span>지금은 다른 라이브가 없어요</span>
            <span className="text-label font-semibold text-brand-700">
              방송 예정 보기
            </span>
          </Link>
        )}
        {/* 카드가 칸 수보다 적으면 남은 칸을 비워 둔다. (카드가 늘어나지 않게) */}
        {side.length > 0 &&
          Array.from({ length: slots - side.length }, (_, i) => (
            <div key={i} aria-hidden="true" className="flex-1" />
          ))}
      </div>
    </section>
  );
}

/**
 * 대표 라이브 큰 칸. 홈에 들어오면 소리 없이 바로 미리보기를 틀고, [소리 켜기]로 소리를 들을 수 있다.
 * 운영체제에서 "동작 줄이기"를 켰으면 자동으로 틀지 않고 마우스를 올렸을 때만 보여 준다.
 */
function FeaturedMain({ card }: { card: Card }) {
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
    <div
      className="relative min-w-0"
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <TileLink card={card} big>
        <PreviewVideo track={preview.videoTrack} />
      </TileLink>
      {canToggleSound && (
        <button
          type="button"
          onClick={toggleSound}
          className="absolute top-4 right-4 flex items-center gap-1.5 rounded-md bg-stage/70 px-3 py-2 text-label font-semibold text-white hover:bg-stage/90"
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
  );
}

/** 대표 라이브 오른쪽 인기 방송 카드. 방송 카드처럼 마우스를 잠시 올려 두면 소리 없이 미리 보여 준다. */
function FeaturedTile({ card }: { card: Card }) {
  const { hovered, onMouseEnter, onMouseLeave } = useHoverIntent();
  const { videoTrack } = useStreamPreview(
    card.liveStreamId ?? 0,
    card.liveStreamId !== undefined && hovered,
  );
  return (
    <div
      className="min-h-0 min-w-0 flex-1"
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
    >
      <TileLink card={card}>
        <PreviewVideo track={videoTrack} />
      </TileLink>
    </div>
  );
}

/**
 * 대표 라이브 칸 하나: 썸네일(또는 미리보기 영상) 위에 LIVE·시청자 수를 위에, 판매자·제목을 아래에 겹쳐 그린다.
 * 큰 칸은 제목을 크게 쓰고 판매자 옆에 해시태그와 시작 시각을 함께 보여 준다.
 * children(미리보기 영상)은 썸네일과 글자 사이에 깔린다.
 */
function TileLink({
  card,
  big = false,
  children,
}: {
  card: Card;
  big?: boolean;
  children?: ReactNode;
}) {
  const seller = card.sellerNickname || "판매자";
  const title = card.title || "제목 없는 방송";
  const hasTags = !!card.hashtags && card.hashtags.length > 0;
  return (
    <Link
      to={`/lives/${card.liveStreamId}`}
      state={{ sellerNickname: card.sellerNickname }}
      aria-label={`${title} 방송 보기`}
      className="group block h-full overflow-hidden rounded-lg ring-brand-500 hover:ring-2"
    >
      <LiveThumbnail
        name={seller}
        imageUrl={card.thumbnailUrl}
        // 큰 칸은 16:9로 영역 높이를 정하고, 작은 칸은 오른쪽 열을 나눠 채운다.
        className={cn("rounded-none", !big && "aspect-auto h-full")}
      >
        {children}
        <div
          className={cn(
            "absolute flex gap-1.5",
            big ? "top-4 left-4" : "top-2.5 left-2.5",
          )}
        >
          <Badge variant="live">LIVE</Badge>
          <Badge className="bg-stage/70 font-semibold text-white">
            <Eye className="size-3.5" aria-hidden="true" />
            <span className="num">{formatNumber(card.viewerCount ?? 0)}</span>
            <span>명</span>
          </Badge>
          {card.recorded && <RecordedBadge />}
        </div>
        <div
          className={cn(
            "absolute inset-x-0 bottom-0 flex items-center bg-linear-to-t from-stage/90 via-stage/50 to-transparent",
            big ? "gap-3.5 px-6 pt-20 pb-5" : "gap-2.5 px-3 pt-8 pb-3",
          )}
        >
          <SellerAvatar name={seller} size={big ? "lg" : "md"} live />
          <span className="flex min-w-0 flex-col gap-0.5 text-left">
            <span
              className={cn(
                "truncate font-bold text-white",
                big ? "text-title-lg tracking-tight" : "text-body",
              )}
            >
              {title}
            </span>
            <span
              className={cn(
                "flex min-w-0 items-center gap-1.5 text-neutral-700",
                big ? "text-body" : "text-caption",
              )}
            >
              <span className="truncate">{seller}</span>
              {big && hasTags && (
                <>
                  <span aria-hidden="true">·</span>
                  <Hashtags
                    tags={card.hashtags}
                    className="flex-nowrap overflow-hidden text-body whitespace-nowrap"
                  />
                </>
              )}
              {big && card.startedAt && (
                <>
                  <span aria-hidden="true">·</span>
                  <span className="shrink-0">
                    {formatDateTime(card.startedAt)} 시작
                  </span>
                </>
              )}
            </span>
          </span>
        </div>
      </LiveThumbnail>
    </Link>
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
