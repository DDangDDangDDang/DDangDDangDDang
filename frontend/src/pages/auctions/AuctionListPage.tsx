import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router';
import { api, call, errorMessage } from '@/api';
import { AuctionCard, AuctionCardSkeleton } from '@/components/auction/AuctionBits';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, buttonVariants, EmptyState, ErrorState, Input, Pagination, Select } from '@/components/ui';
import { categoryFilterOptions, formatAmountInput, parseAmount } from '@/lib/auction';
import { AUCTION_SORT_LABEL, AUCTION_STATUS_LABEL, type AuctionSort, type AuctionStatus } from '@/lib/labels';
import { useCategories } from '@/lib/use-categories';

const PAGE_SIZE = 20;

const STATUS_OPTIONS = [
  { value: '', label: '전체 상태' },
  ...(Object.entries(AUCTION_STATUS_LABEL) as [AuctionStatus, string][]).map(([value, label]) => ({ value, label })),
];
const SORT_OPTIONS = (Object.entries(AUCTION_SORT_LABEL) as [AuctionSort, string][]).map(([value, label]) => ({ value, label }));

type Filters = { q: string; category: string; status: string; sort: string; min: string; max: string };

/** 경매 목록: 상품명 검색 + 카테고리·상태·가격 필터 + 정렬, 번호로 페이지 이동. 조건은 주소에 남겨 새로고침·뒤로가기에도 유지한다. */
export function AuctionListPage() {
  const [params, setParams] = useSearchParams();
  const filters: Filters = {
    q: params.get('q') ?? '',
    category: params.get('category') ?? '',
    status: params.get('status') ?? '',
    sort: params.get('sort') ?? 'LATEST',
    min: params.get('min') ?? '',
    max: params.get('max') ?? '',
  };
  const page = Math.max(0, Number(params.get('page')) || 0);
  const [draft, setDraft] = useState({ q: filters.q, min: filters.min, max: filters.max });
  // 주소의 조건이 바뀌면(뒤로가기, 헤더 메뉴로 다시 들어오기) 입력칸도 실제 목록 조건에 맞춘다.
  const urlDraft = `${filters.q}|${filters.min}|${filters.max}`;
  const [syncedDraft, setSyncedDraft] = useState(urlDraft);
  if (syncedDraft !== urlDraft) {
    setSyncedDraft(urlDraft);
    setDraft({ q: filters.q, min: filters.min, max: filters.max });
  }
  const categories = useCategories();

  const update = (next: Partial<Filters> & { page?: number }) => {
    const merged = { ...filters, page: 0, ...next };
    const search: Record<string, string> = {};
    for (const [key, value] of Object.entries(merged)) {
      if (key === 'page' ? Number(value) > 0 : value && !(key === 'sort' && value === 'LATEST')) search[key] = String(value);
    }
    // 페이지 이동은 뒤로가기로 돌아올 수 있게 기록을 남기고, 필터 변경은 바꿔치기한다.
    setParams(search, { replace: !('page' in next) });
  };

  const query = useQuery({
    queryKey: ['auctions', 'list', filters, page],
    queryFn: () =>
      call(
        api.GET('/api/v1/auctions', {
          params: {
            query: {
              productName: filters.q || undefined,
              categoryId: filters.category ? Number(filters.category) : undefined,
              status: (filters.status || undefined) as AuctionStatus | undefined,
              sortType: filters.sort as AuctionSort,
              minPrice: parseAmount(filters.min),
              maxPrice: parseAmount(filters.max),
              page,
              size: PAGE_SIZE,
            },
          },
        }),
      ),
    placeholderData: keepPreviousData,
  });

  const onSearch = (e: FormEvent) => {
    e.preventDefault();
    update({ q: draft.q.trim(), min: String(parseAmount(draft.min) ?? ''), max: String(parseAmount(draft.max) ?? '') });
  };

  const auctions = query.data?.content ?? [];
  const filtered = Boolean(filters.q || filters.category || filters.status || filters.min || filters.max);

  return (
    <div>
      <PageHeader
        title="경매"
        description="진행 중인 경매를 찾아보세요. 방송 중 경매는 방송 화면에서도 볼 수 있어요."
        actions={
          <Link to="/products/new" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
            상품 등록
          </Link>
        }
      />

      <form role="search" onSubmit={onSearch} className="mb-4 flex items-end gap-3 rounded-lg border border-neutral-100 bg-neutral-0 p-4">
        <div className="flex-1">
          <Input label="상품명" type="search" placeholder="상품명으로 검색" maxLength={50} value={draft.q} onChange={(e) => setDraft({ ...draft, q: e.target.value })} />
        </div>
        <div className="w-[200px]">
          <Select
            label="카테고리"
            options={[{ value: '', label: '전체 카테고리' }, ...categoryFilterOptions(categories.data ?? [])]}
            value={filters.category}
            onChange={(e) => update({ category: e.target.value })}
          />
        </div>
        <div className="w-[140px]">
          <Input label="최소 가격" inputMode="numeric" suffix="P" value={formatAmountInput(draft.min)} onChange={(e) => setDraft({ ...draft, min: e.target.value })} />
        </div>
        <div className="w-[140px]">
          <Input label="최대 가격" inputMode="numeric" suffix="P" value={formatAmountInput(draft.max)} onChange={(e) => setDraft({ ...draft, max: e.target.value })} />
        </div>
        <Button type="submit" variant="secondary" size="icon" aria-label="검색">
          <Search className="size-[18px]" aria-hidden="true" />
        </Button>
      </form>

      <div className="mb-6 flex items-center justify-between gap-4">
        <span className="text-label text-neutral-500">
          {query.data ? (
            <>
              총 <span className="num font-semibold text-neutral-900">{(query.data.totalElements ?? 0).toLocaleString('ko-KR')}</span>건
            </>
          ) : (
            ' '
          )}
        </span>
        <div className="flex gap-3">
          <div className="w-[160px]">
            <Select label="상태" hideLabel options={STATUS_OPTIONS} value={filters.status} onChange={(e) => update({ status: e.target.value })} />
          </div>
          <div className="w-[160px]">
            <Select label="정렬" hideLabel options={SORT_OPTIONS} value={filters.sort} onChange={(e) => update({ sort: e.target.value })} />
          </div>
        </div>
      </div>

      {query.isPending ? (
        <Grid>
          {Array.from({ length: 8 }, (_, i) => (
            <AuctionCardSkeleton key={i} />
          ))}
        </Grid>
      ) : query.isError ? (
        <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : auctions.length === 0 ? (
        filtered ? (
          <EmptyState title="조건에 맞는 경매가 없어요" description="검색어나 필터를 바꿔 보세요." />
        ) : (
          <EmptyState title="아직 등록된 경매가 없어요" description="판매자가 상품을 등록하고 경매를 열면 여기에 나와요." />
        )
      ) : (
        <Grid>
          {auctions.map((auction) => (
            <AuctionCard key={auction.auctionId} auction={auction} />
          ))}
        </Grid>
      )}

      <Pagination page={page} totalPages={query.data?.totalPages ?? 0} onChange={(next) => update({ page: next })} label="경매 목록 페이지" />
    </div>
  );
}

function Grid({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-4 gap-x-5 gap-y-8">{children}</div>;
}
