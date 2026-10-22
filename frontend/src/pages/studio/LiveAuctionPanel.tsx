import { Gavel, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { errorMessage, type Schema } from '@/api';
import { AuctionPhaseBadge, AuctionTime } from '@/components/auction/AuctionBits';
import { AuctionConditionFields } from '@/components/auction/AuctionConditionFields';
import { Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, ErrorState, Notice, Skeleton } from '@/components/ui';
import { auctionPhase, EMPTY_AUCTION_CONDITION, validateAuctionCondition, type AuctionConditionErrors, type AuctionConditionValues } from '@/lib/auction';
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

/** 이 방송에 올라간 경매: 진행 중인 것을 위에, 끝난 것은 아래에. 현재가·입찰 수·남은 시간과 취소 버튼. */
export function LiveAuctionStatus({ auctions }: { auctions: LiveAuctions }) {
  const now = useNow(1000);
  const list = auctions.auctions;
  const running = list.filter((a) => auctionPhase(a, now) === 'ONGOING');
  const others = list.filter((a) => auctionPhase(a, now) !== 'ONGOING');
  const cancel = auctions.cancel;
  const [cancelTarget, setCancelTarget] = useState<Schema<'AuctionResponse'>>();

  return (
    <Card aria-labelledby="running-auctions-title">
      <CardHeader>
        <CardTitle id="running-auctions-title" className="flex items-center gap-2 text-body-lg">
          진행 중인 경매 <span className="num text-brand-700">{running.length}</span>
        </CardTitle>
      </CardHeader>
      <CardBody>
        <ul aria-live="polite" aria-label="이 방송의 경매" className="flex max-h-[360px] flex-col gap-2 overflow-y-auto">
          {auctions.loadingAuctions && list.length === 0 ? (
            <li>
              <Skeleton className="h-24" />
            </li>
          ) : list.length === 0 ? (
            <li className="rounded-md bg-neutral-25 px-3 py-3 text-label text-neutral-500">경매 상품에서 [경매 시작]이나 [이 방송에 올리기]를 누르면 여기에서 현재가와 남은 시간을 볼 수 있어요.</li>
          ) : (
            [...running, ...others].map((auction) => {
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
                  {phase === 'ONGOING' && (auction.bidCount ?? 0) === 0 && (
                    <Button variant="outline" size="sm" className="self-end" onClick={() => setCancelTarget(auction)}>
                      경매 취소
                    </Button>
                  )}
                </li>
              );
            })
          )}
        </ul>
      </CardBody>
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
