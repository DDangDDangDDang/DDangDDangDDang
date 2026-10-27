import { useInfiniteQuery } from "@tanstack/react-query";
import { Search } from "lucide-react";
import { useState, type FormEvent, type ReactNode } from "react";
import { useSearchParams } from "react-router";
import { api, call, errorMessage } from "@/api";
import { PageHeader } from "@/components/layout/PageHeader";
import {
  LiveStreamCard,
  LiveStreamCardSkeleton,
} from "@/components/live/LiveStreamCard";
import { Button, EmptyState, ErrorState, Input } from "@/components/ui";
import { cn } from "@/lib/cn";
import type { LiveStreamStatus } from "@/lib/labels";
import { useInfiniteScroll } from "@/lib/use-infinite-scroll";

const PAGE_SIZE = 20;

type Tab = { status: LiveStreamStatus; label: string; empty: string };
const LIVE_TAB: Tab = {
  status: "LIVE",
  label: "방송 중",
  empty: "지금 라이브 중인 방송이 없어요",
};
const TABS: Tab[] = [
  LIVE_TAB,
  { status: "SCHEDULED", label: "방송 예정", empty: "예정된 방송이 없어요" },
  { status: "ENDED", label: "지난 방송", empty: "끝난 방송이 없어요" },
];

/** 방송 목록: 상태별(방송 중·예정·지난 방송) + 검색(제목·설명·판매자 닉네임). 스크롤하면 계속 불러온다. */
export function LiveListPage() {
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((t) => t.status === params.get("status")) ?? LIVE_TAB;
  const status = tab.status;
  const keyword = params.get("q")?.trim() || undefined;
  const [draft, setDraft] = useState(keyword ?? "");

  const query = useInfiniteQuery({
    queryKey: ["live-streams", "list", status, keyword ?? ""],
    queryFn: ({ pageParam }) =>
      call(
        api.GET("/api/v1/live-streams", {
          params: {
            query: { status, keyword, cursor: pageParam, size: PAGE_SIZE },
          },
        }),
      ),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });

  const sentinelRef = useInfiniteScroll(
    query.hasNextPage,
    query.isFetchingNextPage,
    query.fetchNextPage,
  );
  const cards = query.data?.pages.flatMap((page) => page?.content ?? []) ?? [];

  const update = (next: { status?: LiveStreamStatus; q?: string }) => {
    const merged = { status, q: keyword ?? "", ...next };
    const search: Record<string, string> = {};
    if (merged.status !== "LIVE") search.status = merged.status;
    if (merged.q.trim()) search.q = merged.q.trim();
    setParams(search, { replace: true });
  };

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    update({ q: draft });
  };

  return (
    <div>
      <PageHeader title="라이브 방송" />

      <div className="mb-6 flex items-center justify-between gap-4">
        <nav
          aria-label="방송 상태"
          className="flex gap-1 rounded-md bg-neutral-50 p-1"
        >
          {TABS.map((t) => (
            <button
              key={t.status}
              type="button"
              aria-pressed={t.status === status}
              onClick={() => update({ status: t.status })}
              className={cn(
                "h-9 rounded-md px-4 text-body font-semibold transition-colors",
                t.status === status
                  ? "bg-neutral-0 text-neutral-900 shadow-sm"
                  : "text-neutral-500 hover:text-neutral-900",
              )}
            >
              {t.label}
            </button>
          ))}
        </nav>
        <form
          role="search"
          onSubmit={onSearch}
          className="flex w-[420px] items-end gap-2"
        >
          <div className="flex-1">
            <Input
              label="방송 검색"
              hideLabel
              type="search"
              placeholder="방송 제목, #해시태그, 판매자 닉네임"
              maxLength={50}
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
            />
          </div>
          <Button
            type="submit"
            variant="secondary"
            size="icon"
            aria-label="검색"
          >
            <Search className="size-[18px]" aria-hidden="true" />
          </Button>
        </form>
      </div>

      {query.isPending ? (
        <Grid>
          {Array.from({ length: 8 }, (_, i) => (
            <LiveStreamCardSkeleton key={i} />
          ))}
        </Grid>
      ) : query.isError ? (
        <ErrorState
          message={errorMessage(query.error)}
          onRetry={() => void query.refetch()}
        />
      ) : cards.length === 0 ? (
        keyword ? (
          <EmptyState
            title={`'${keyword}' 검색 결과가 없어요`}
            description="다른 검색어로 찾아보거나 다른 탭을 확인해 보세요."
          />
        ) : (
          <EmptyState
            title={tab.empty}
            description="판매자가 방송을 시작하거나 예약하면 여기에 나와요."
          />
        )
      ) : (
        <Grid>
          {cards.map((card) => (
            <LiveStreamCard key={card.liveStreamId} card={card} />
          ))}
        </Grid>
      )}
      <div ref={sentinelRef} />
      {query.isFetchingNextPage && (
        <div className="mt-8">
          <Grid>
            {Array.from({ length: 4 }, (_, i) => (
              <LiveStreamCardSkeleton key={i} />
            ))}
          </Grid>
        </div>
      )}
    </div>
  );
}

function Grid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-4 gap-x-5 gap-y-8">{children}</div>;
}
