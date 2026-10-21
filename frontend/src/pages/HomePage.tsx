import { useQuery } from '@tanstack/react-query';
import { Gavel } from 'lucide-react';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { LiveStreamCard, LiveStreamCardSkeleton } from '@/components/live/LiveStreamCard';
import { buttonVariants, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { formatDateTime } from '@/lib/format';

/** 섹션별로 보여줄 방송 수 (한 줄 4개씩 2줄) */
const SECTION_SIZE = 8;

/**
 * 홈: 인기 라이브 · 시청자 급상승 · 방송 예정.
 * 방송 API는 로그인해야 쓸 수 있어서, 로그인 전에는 서비스 소개와 로그인 안내만 보여준다.
 */
export function HomePage() {
  const session = useSession();
  return session ? <LiveHome /> : <GuestHome />;
}

function GuestHome() {
  return (
    <section className="flex flex-col items-center gap-4 rounded-lg border border-neutral-100 bg-neutral-0 px-8 py-20 text-center shadow-sm">
      <span className="flex size-14 items-center justify-center rounded-lg bg-brand-500 text-neutral-0">
        <Gavel className="size-7" aria-hidden="true" />
      </span>
      <h1 className="text-display font-bold tracking-tight">라이브로 보고, 실시간으로 입찰하세요</h1>
      <p className="text-body-lg text-neutral-500">판매자의 방송을 보며 채팅하고, 마음에 드는 상품에 바로 입찰할 수 있어요.</p>
      <div className="mt-2 flex gap-2">
        <Link to="/login" className={buttonVariants({ variant: 'primary', size: 'lg' })}>
          로그인하고 방송 보기
        </Link>
        <Link to="/signup" className={buttonVariants({ variant: 'outline', size: 'lg' })}>
          회원가입
        </Link>
      </div>
    </section>
  );
}

function LiveHome() {
  const query = useQuery({
    queryKey: ['live-streams', 'home', SECTION_SIZE],
    queryFn: () => call(api.GET('/api/v1/live-streams/home', { params: { query: { size: SECTION_SIZE } } })),
  });

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-12">
        <Section title="지금 인기 라이브">
          <CardGrid>
            {Array.from({ length: 4 }, (_, i) => (
              <LiveStreamCardSkeleton key={i} />
            ))}
          </CardGrid>
        </Section>
        <Section title="방송 예정">
          <div className="flex flex-col gap-3">
            {Array.from({ length: 2 }, (_, i) => (
              <Skeleton key={i} className="h-20" />
            ))}
          </div>
        </Section>
      </div>
    );
  }

  if (query.isError) {
    return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  }

  const mostViewed = query.data?.mostViewed ?? [];
  const surging = query.data?.surging ?? [];
  const upcoming = query.data?.upcoming ?? [];

  if (mostViewed.length === 0 && surging.length === 0 && upcoming.length === 0) {
    return (
      <EmptyState
        title="지금은 진행 중인 방송이 없어요"
        description="판매자가 방송을 시작하거나 예약하면 여기에 나와요."
        action={
          <Link to="/lives?status=ENDED" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            지난 방송 보기
          </Link>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-12">
      <Section title="지금 인기 라이브" moreTo="/lives">
        {mostViewed.length === 0 ? (
          <p className="rounded-lg border border-dashed border-neutral-200 px-5 py-8 text-center text-body text-neutral-500">
            지금 라이브 중인 방송이 없어요. 아래 예정된 방송을 확인해 보세요.
          </p>
        ) : (
          <CardGrid>
            {mostViewed.map((card) => (
              <LiveStreamCard key={card.liveStreamId} card={card} />
            ))}
          </CardGrid>
        )}
      </Section>

      {surging.length > 0 && (
        <Section title="시청자가 몰리는 방송" description="최근 시청자가 빠르게 늘고 있어요.">
          <CardGrid>
            {surging.map((card) => (
              <LiveStreamCard key={card.liveStreamId} card={card} showIncrease />
            ))}
          </CardGrid>
        </Section>
      )}

      {upcoming.length > 0 && (
        <Section title="방송 예정" moreTo="/lives?status=SCHEDULED">
          <ul className="flex flex-col gap-3">
            {upcoming.map((card) => (
              <li key={card.liveStreamId}>
                <UpcomingRow card={card} />
              </li>
            ))}
          </ul>
        </Section>
      )}
    </div>
  );
}

function Section({ title, description, moreTo, children }: { title: string; description?: string; moreTo?: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="flex flex-col gap-4">
      <div className="flex items-end justify-between gap-3">
        <div className="flex flex-col gap-0.5">
          <h2 className="text-title-lg font-bold tracking-tight">{title}</h2>
          {description && <p className="text-body text-neutral-500">{description}</p>}
        </div>
        {moreTo && (
          <Link to={moreTo} className="text-body font-semibold text-neutral-500 hover:text-neutral-900">
            전체 보기
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

function UpcomingRow({ card }: { card: Schema<'LiveStreamCard'> }) {
  return (
    <Link
      to={`/lives/${card.liveStreamId}`}
      state={{ sellerNickname: card.sellerNickname }}
      className="flex items-center gap-4 rounded-lg border border-neutral-100 bg-neutral-0 p-4 shadow-sm hover:bg-neutral-25"
    >
      <span className="w-[180px] shrink-0 text-label font-bold text-brand-700">
        {card.scheduledStartAt ? formatDateTime(card.scheduledStartAt) : '시작 시간 미정'}
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <strong className="truncate text-body-lg font-semibold">{card.title || '제목 없는 방송'}</strong>
        <span className="text-label text-neutral-500">{card.sellerNickname || '판매자'}</span>
      </span>
    </Link>
  );
}
