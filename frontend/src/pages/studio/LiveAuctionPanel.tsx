import { Gavel, MonitorPlay, Plus } from 'lucide-react';
import { useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { Link } from 'react-router';
import { errorMessage, isErrorCode, type Schema } from '@/api';
import { AuctionPhaseBadge, AuctionTime } from '@/components/auction/AuctionBits';
import { AuctionConditionFields } from '@/components/auction/AuctionConditionFields';
import { Badge, Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, Dialog, ErrorState, Notice, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import {
  auctionPhase,
  auctionStatusTab,
  type AuctionStatusTab,
  EMPTY_AUCTION_CONDITION,
  featuredAuctions,
  MAX_FEATURED_AUCTIONS,
  validateAuctionCondition,
  type AuctionConditionErrors,
  type AuctionConditionValues,
} from '@/lib/auction';
import { formatDateTime, formatNumber, formatPoint } from '@/lib/format';
import { useNow } from '@/lib/use-now';
import { ProductCreateDialog } from './ProductCreateDialog';
import type { LiveAuctions } from './use-live-auctions';

type Product = Schema<'ProductResponse'>;

/**
 * 경매 상품: 내 판매 중 상품 목록에서 [경매 시작] → 시작가·입찰 단위·진행 시간을 정해 방송 중 경매를 연다.
 * 미리 만든(시작 전·진행 중) 경매가 있는 상품은 [이 방송에 올리기]로 그 경매를 방송에 보여준다. (일정은 그대로)
 * [상품 등록]은 창으로 열어 스튜디오를 떠나지 않고 등록하고, 방송 중이면 그 상품의 경매 조건 입력을 바로 연다.
 */
export function LiveAuctionProducts({ live, auctions }: { live: boolean; auctions: LiveAuctions }) {
  const [selected, setSelected] = useState<number>();
  const [creating, setCreating] = useState(false);
  const products = auctions.products;

  return (
    <Card aria-labelledby="studio-auction-title">
      <CardHeader>
        <div className="flex flex-col gap-0.5">
          <CardTitle id="studio-auction-title">경매 상품</CardTitle>
          <span className="text-caption text-neutral-500">여러 상품을 동시에 경매할 수 있어요. 시작하면 바로 진행되고 정한 시간이 지나면 마감돼요.</span>
        </div>
        <Button variant="outline" size="sm" onClick={() => setCreating(true)}>
          <Plus className="size-4" aria-hidden="true" />
          상품 등록
        </Button>
      </CardHeader>
      <CardBody>
        {!live && <Notice className="mb-3">방송을 시작하면 상품을 골라 바로 경매를 열 수 있어요.</Notice>}
        {products.isPending ? (
          <div className="flex flex-col gap-2">
            <Skeleton className="h-12" />
            <Skeleton className="h-12" />
          </div>
        ) : products.isError ? (
          <ErrorState message={errorMessage(products.error)} onRetry={() => void products.refetch()} />
        ) : (products.data ?? []).length === 0 ? (
          <div className="flex items-center gap-4 rounded-md bg-neutral-25 px-4 py-4">
            <Gavel className="size-6 shrink-0 text-neutral-400" aria-hidden="true" />
            <p className="text-body text-neutral-700">경매에 올릴 판매 중인 상품이 없어요. [상품 등록]으로 바로 등록할 수 있어요.</p>
          </div>
        ) : (
          <ul className="divide-y divide-neutral-100">
            {(products.data ?? []).map((product) => (
              <li key={product.id} className="py-3">
                <ProductRow
                  product={product}
                  live={live}
                  open={selected === product.id}
                  activeAuction={auctions.activeAuctionOf(product.id)}
                  onOpen={() => setSelected(product.id)}
                  onClose={() => setSelected(undefined)}
                  auctions={auctions}
                />
              </li>
            ))}
          </ul>
        )}
      </CardBody>
      <ProductCreateDialog open={creating} live={live} onClose={() => setCreating(false)} onCreated={(productId) => live && setSelected(productId)} />
    </Card>
  );
}

function ProductRow({
  product,
  live,
  open,
  activeAuction,
  onOpen,
  onClose,
  auctions,
}: {
  product: Product;
  live: boolean;
  open: boolean;
  activeAuction?: Schema<'AuctionResponse'>;
  onOpen: () => void;
  onClose: () => void;
  auctions: LiveAuctions;
}) {
  const [values, setValues] = useState<AuctionConditionValues>(EMPTY_AUCTION_CONDITION);
  const [errors, setErrors] = useState<AuctionConditionErrors>({});
  const start = auctions.start;
  const startingThis = start.isPending && start.variables?.productId === product.id;
  const failedThis = start.isError && start.variables?.productId === product.id;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const next = validateAuctionCondition(values, 'live', new Date());
    setErrors(next);
    if (Object.keys(next).length > 0 || !product.id) return;
    start.mutate(
      { productId: product.id, values },
      {
        onSuccess: () => {
          setValues(EMPTY_AUCTION_CONDITION);
          onClose();
        },
      },
    );
  };

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-3">
        <div className="flex min-w-0 flex-col">
          <strong className="truncate text-body font-semibold">{product.name || '이름 없는 상품'}</strong>
          <span className="text-caption text-neutral-500">{product.categoryName ?? ''}</span>
        </div>
        {activeAuction ? (
          <ActiveAuction auction={activeAuction} live={live} auctions={auctions} />
        ) : open ? null : (
          <Button size="sm" variant="secondary" disabled={!live} onClick={onOpen}>
            경매 시작
          </Button>
        )}
      </div>
      {open && !activeAuction && (
        <form onSubmit={onSubmit} noValidate aria-label={`${product.name ?? '상품'} 경매 조건`} className="flex flex-col gap-3 rounded-md bg-neutral-25 p-4">
          <AuctionConditionFields
            mode="live"
            compact
            values={values}
            errors={errors}
            onChange={(patch) => {
              setValues((prev) => ({ ...prev, ...patch }));
              setErrors({});
            }}
          />
          {failedThis && <Notice tone="error">{errorMessage(start.error)}</Notice>}
          <div className="flex justify-end gap-2">
            <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={startingThis}>
              닫기
            </Button>
            <Button type="submit" size="sm" loading={startingThis}>
              이 조건으로 경매 시작
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}

/**
 * 상품에 이미 있는 경매. 이 방송에 올라가 있으면 상태만, 아니면 [이 방송에 올리기]를 보여준다.
 */
function ActiveAuction({ auction, live, auctions }: { auction: Schema<'AuctionResponse'>; live: boolean; auctions: LiveAuctions }) {
  const now = useNow(auction.status === 'READY' ? 1000 : null);
  const ready = auctionPhase(auction, now) === 'READY';
  const show = auctions.show;
  const showingThis = show.isPending && show.variables === auction.id;
  const failedThis = show.isError && show.variables === auction.id;
  const when = ready && auction.startAt ? `${formatDateTime(auction.startAt)} 시작` : '진행 중';

  if (auction.liveStreamId === auctions.liveStreamId) {
    return <span className="text-label font-semibold text-brand-700">{ready ? `이 방송 · ${when}` : '이 방송에서 경매 중'}</span>;
  }
  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex items-center gap-2">
        <span className="text-label text-neutral-500">{ready ? `예약된 경매 · ${when}` : '진행 중인 경매'}</span>
        <Button size="sm" variant="secondary" disabled={!live} loading={showingThis} onClick={() => auction.id && show.mutate(auction.id)}>
          이 방송에 올리기
        </Button>
      </div>
      {failedThis && <Notice tone="error">{errorMessage(show.error)}</Notice>}
    </div>
  );
}

/**
 * 영상 위에 띄우기·내리기. 최대 MAX_FEATURED_AUCTIONS(2)개까지 띄운다.
 * 이미 2개가 떠 있으면 [영상에 띄우기]는 꺼진 모양으로 보이고, 누르면(onBlocked) 하나를 내리라는 창을 연다.
 * (진짜 disabled면 눌러도 아무 일이 없어 이유를 알 수 없으므로 aria-disabled로 두고 눌림은 받는다)
 */
function FeatureButton({
  auction,
  auctions,
  featuredNow,
  full,
  onBlocked,
}: {
  auction: Schema<'AuctionResponse'>;
  auctions: LiveAuctions;
  featuredNow: boolean;
  full: boolean;
  onBlocked: () => void;
}) {
  const feature = auctions.feature;
  const pendingThis = feature.isPending && feature.variables?.auctionId === auction.id;
  const blocked = !featuredNow && full;
  const name = auction.productName || '상품';
  return (
    <Button
      size="sm"
      variant={featuredNow ? 'outline' : 'secondary'}
      aria-label={`${name} ${featuredNow ? '영상에서 내리기' : '영상에 띄우기'}`}
      aria-disabled={blocked || undefined}
      className={cn(blocked && 'cursor-not-allowed opacity-50')}
      loading={pendingThis}
      disabled={feature.isPending && !pendingThis}
      onClick={() => {
        if (blocked) {
          onBlocked();
          return;
        }
        if (auction.id) feature.mutate({ auctionId: auction.id, on: !featuredNow });
      }}
    >
      {featuredNow ? '영상에서 내리기' : '영상에 띄우기'}
    </Button>
  );
}

/** 이미 2개가 떠 있을 때 띄우려고 하면 여는 안내 창. 지금 띄운 경매를 여기서 바로 내릴 수 있다. */
function FeatureLimitDialog({
  open,
  featured,
  auctions,
  onClose,
}: {
  open: boolean;
  featured: Schema<'AuctionResponse'>[];
  auctions: LiveAuctions;
  onClose: () => void;
}) {
  const feature = auctions.feature;
  return (
    <Dialog
      open={open}
      title={`영상 위에는 경매를 ${MAX_FEATURED_AUCTIONS}개까지 띄울 수 있어요`}
      description="이미 2개가 떠 있어요. 띄운 경매 하나를 내린 뒤 다시 띄워 주세요."
      onClose={onClose}
      className="w-[480px]"
    >
      <ul aria-label="영상에 띄운 경매" className="flex flex-col gap-2">
        {featured.map((auction) => (
          <li key={auction.id} className="flex items-center justify-between gap-3 rounded-md bg-neutral-25 px-3 py-2.5">
            <span className="flex min-w-0 items-center gap-1.5">
              <MonitorPlay className="size-4 shrink-0 text-info-700" aria-hidden="true" />
              <strong className="truncate text-body font-semibold">{auction.productName || '상품'}</strong>
            </span>
            <Button
              size="sm"
              variant="outline"
              aria-label={`${auction.productName || '상품'} 영상에서 내리기`}
              loading={feature.isPending && feature.variables?.auctionId === auction.id}
              disabled={feature.isPending && feature.variables?.auctionId !== auction.id}
              onClick={() => auction.id && feature.mutate({ auctionId: auction.id, on: false }, { onSuccess: onClose })}
            >
              영상에서 내리기
            </Button>
          </li>
        ))}
      </ul>
      <div className="mt-5 flex justify-end">
        <Button variant="secondary" onClick={onClose}>
          확인
        </Button>
      </div>
    </Dialog>
  );
}

/** 이 방송에 올라간 경매: 진행 중인 것을 위에, 끝난 것은 아래에. 현재가·입찰 수·남은 시간과 취소 버튼. */
const STATUS_TABS: { key: AuctionStatusTab; label: string; empty: string }[] = [
  { key: 'ONGOING', label: '진행 중', empty: '지금 진행 중인 경매가 없어요.' },
  { key: 'READY', label: '시작 전', empty: '시작을 기다리는 경매가 없어요.' },
  { key: 'ENDED', label: '종료', empty: '아직 끝난 경매가 없어요.' },
];

export function LiveAuctionStatus({ auctions }: { auctions: LiveAuctions }) {
  const now = useNow(1000);
  const list = auctions.auctions;
  // 상태 탭: 기본은 진행 중. 시작 전·종료 경매는 탭을 바꿔서 본다.
  const [tab, setTab] = useState<AuctionStatusTab>('ONGOING');
  const byTab = (key: AuctionStatusTab) => list.filter((a) => auctionStatusTab(auctionPhase(a, now)) === key);
  const shown = byTab(tab);
  // 탭 패턴: 좌우 화살표·Home·End로 탭을 옮기고, 옮긴 탭에 포커스를 준다.
  const tabRefs = useRef<Partial<Record<AuctionStatusTab, HTMLButtonElement | null>>>({});
  const moveTab = (event: KeyboardEvent<HTMLButtonElement>) => {
    const index = STATUS_TABS.findIndex((item) => item.key === tab);
    const last = STATUS_TABS.length - 1;
    const next = { ArrowRight: index === last ? 0 : index + 1, ArrowLeft: index === 0 ? last : index - 1, Home: 0, End: last }[event.key];
    const target = next === undefined ? undefined : STATUS_TABS[next];
    if (!target) return;
    event.preventDefault();
    setTab(target.key);
    tabRefs.current[target.key]?.focus();
  };
  const cancel = auctions.cancel;
  const [cancelTarget, setCancelTarget] = useState<Schema<'AuctionResponse'>>();
  const featured = featuredAuctions(auctions.featuredAuctionIds, list, now);
  const featuredIds = new Set(featured.map((a) => a.id));
  const full = featured.length >= MAX_FEATURED_AUCTIONS;
  const feature = auctions.feature;
  // 2개가 떠 있는데 더 띄우려고 함: 버튼을 눌렀거나, 그새 다른 탭에서 띄워 서버가 막았을 때(LIVESTREAM_017)
  const [limitOpen, setLimitOpen] = useState(false);
  const limitError = feature.isError && isErrorCode(feature.error, 'LIVESTREAM_017');
  const [prevLimitError, setPrevLimitError] = useState(limitError);
  if (prevLimitError !== limitError) {
    setPrevLimitError(limitError);
    if (limitError) setLimitOpen(true);
  }

  return (
    <Card aria-labelledby="running-auctions-title">
      <CardHeader>
        <CardTitle id="running-auctions-title" className="flex items-center gap-2 text-body-lg">
          이 방송의 경매 <span className="num text-brand-700">{list.length}</span>
        </CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-3">
        {list.length > 0 && featured.length === 0 && (
          <p className="text-caption text-neutral-500">[영상에 띄우기]를 누르면 시청자 영상 위에 그 경매의 현재가와 남은 시간이 보여요. 2개까지 함께 띄울 수 있어요.</p>
        )}
        {featured.length > 0 && (
          <p className="text-caption text-neutral-500">
            영상에 띄운 경매 <span className="num font-semibold text-neutral-900">{featured.length}</span>/{MAX_FEATURED_AUCTIONS}
          </p>
        )}
        {feature.isError && !limitError && <Notice tone="error">{errorMessage(feature.error)}</Notice>}
        <div role="tablist" aria-label="경매 상태" className="flex gap-1 rounded-md bg-neutral-50 p-1">
          {STATUS_TABS.map((item) => (
            <button
              key={item.key}
              ref={(el) => {
                tabRefs.current[item.key] = el;
              }}
              id={`auction-tab-${item.key}`}
              type="button"
              role="tab"
              aria-selected={tab === item.key}
              aria-controls="auction-tabpanel"
              tabIndex={tab === item.key ? 0 : -1}
              onClick={() => setTab(item.key)}
              onKeyDown={moveTab}
              className={cn(
                'h-8 flex-1 rounded-sm px-2 text-label font-semibold transition-colors',
                tab === item.key ? 'bg-neutral-0 text-neutral-900 shadow-sm' : 'text-neutral-500 hover:text-neutral-900',
              )}
            >
              {item.label} <span className="num">{byTab(item.key).length}</span>
            </button>
          ))}
        </div>
        <div id="auction-tabpanel" role="tabpanel" aria-labelledby={`auction-tab-${tab}`}>
          <ul aria-live="polite" aria-label="이 방송의 경매" className="flex max-h-[360px] flex-col gap-2 overflow-y-auto">
            {auctions.loadingAuctions && list.length === 0 ? (
              <li>
                <Skeleton className="h-24" />
              </li>
            ) : list.length === 0 ? (
              <li className="rounded-md bg-neutral-25 px-3 py-3 text-label text-neutral-500">경매 상품에서 [경매 시작]이나 [이 방송에 올리기]를 누르면 여기에서 현재가와 남은 시간을 볼 수 있어요.</li>
            ) : shown.length === 0 ? (
              <li className="rounded-md bg-neutral-25 px-3 py-3 text-label text-neutral-500">{STATUS_TABS.find((item) => item.key === tab)?.empty}</li>
            ) : (
              shown.map((auction) => {
                const phase = auctionPhase(auction, now);
                return (
                  <li key={auction.id} className="flex flex-col gap-2 rounded-md bg-neutral-25 px-3 py-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="flex min-w-0 items-center gap-1.5">
                        <AuctionPhaseBadge phase={phase} />
                        <Link to={`/auctions/${auction.id}`} target="_blank" rel="noreferrer" className="truncate text-body font-semibold hover:underline">
                          {auction.productName || '상품'}
                        </Link>
                      </span>
                      <AuctionTime status={auction.status} startAt={auction.startAt} endAt={auction.endAt} />
                    </div>
                    <dl className="grid grid-cols-2 gap-1 text-caption">
                      <div>
                        <dt className="text-neutral-500">{phase === 'ENDED' ? '낙찰가' : '현재가'}</dt>
                        <dd className="num text-body font-bold">{formatPoint(auction.currentPrice ?? 0)}</dd>
                      </div>
                      <div>
                        <dt className="text-neutral-500">입찰 수</dt>
                        <dd className="text-body font-bold">
                          <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
                        </dd>
                      </div>
                    </dl>
                    {(phase === 'READY' || phase === 'ONGOING') && (
                      <div className="flex items-center justify-between gap-2">
                        {featuredIds.has(auction.id) ? (
                          <Badge variant="info">
                            <MonitorPlay className="size-3.5" aria-hidden="true" />
                            영상에 띄움
                          </Badge>
                        ) : (
                          <span />
                        )}
                        <div className="flex gap-2">
                          {phase === 'ONGOING' && (auction.bidCount ?? 0) === 0 && (
                            <Button variant="outline" size="sm" onClick={() => setCancelTarget(auction)}>
                              경매 취소
                            </Button>
                          )}
                          <FeatureButton
                            auction={auction}
                            auctions={auctions}
                            featuredNow={featuredIds.has(auction.id)}
                            full={full}
                            onBlocked={() => setLimitOpen(true)}
                          />
                        </div>
                      </div>
                    )}
                  </li>
                );
              })
            )}
          </ul>
        </div>
      </CardBody>
      <FeatureLimitDialog
        open={limitOpen}
        featured={featured}
        auctions={auctions}
        onClose={() => {
          setLimitOpen(false);
          if (limitError) feature.reset();
        }}
      />
      <ConfirmDialog
        open={cancelTarget !== undefined}
        title="경매를 취소할까요?"
        description={
          <div className="flex flex-col gap-2">
            <p>‘{cancelTarget?.productName}’ 경매를 취소하면 되돌릴 수 없어요. 상품은 다시 경매에 올릴 수 있어요.</p>
            {cancel.isError && <Notice tone="error">{errorMessage(cancel.error)}</Notice>}
          </div>
        }
        confirmLabel="경매 취소"
        cancelLabel="닫기"
        loading={cancel.isPending}
        onConfirm={() => cancelTarget?.id && cancel.mutate(cancelTarget.id, { onSuccess: () => setCancelTarget(undefined) })}
        onCancel={() => {
          setCancelTarget(undefined);
          cancel.reset();
        }}
      />
    </Card>
  );
}
