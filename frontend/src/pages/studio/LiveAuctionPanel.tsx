import { Gavel } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { errorMessage, type Schema } from '@/api';
import { AuctionPhaseBadge, AuctionTime } from '@/components/auction/AuctionBits';
import { AuctionConditionFields } from '@/components/auction/AuctionConditionFields';
import { Button, buttonVariants, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, ErrorState, Notice, Skeleton } from '@/components/ui';
import { auctionPhase, EMPTY_AUCTION_CONDITION, validateAuctionCondition, type AuctionConditionErrors, type AuctionConditionValues } from '@/lib/auction';
import { formatNumber, formatPoint } from '@/lib/format';
import { useNow } from '@/lib/use-now';
import type { LiveAuctions } from './use-live-auctions';

type Product = Schema<'ProductResponse'>;

/** 경매 상품: 내 판매 중 상품 목록에서 [경매 시작] → 시작가·입찰 단위·진행 시간을 정해 방송 중 경매를 연다. */
export function LiveAuctionProducts({ live, auctions }: { live: boolean; auctions: LiveAuctions }) {
  const [selected, setSelected] = useState<number>();
  const products = auctions.products;

  return (
    <Card aria-labelledby="studio-auction-title">
      <CardHeader>
        <div className="flex flex-col gap-0.5">
          <CardTitle id="studio-auction-title">경매 상품</CardTitle>
          <span className="text-caption text-neutral-500">여러 상품을 동시에 경매할 수 있어요. 시작하면 바로 진행되고 정한 시간이 지나면 마감돼요.</span>
        </div>
        <Link to="/products/new" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
          상품 등록
        </Link>
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
            <p className="text-body text-neutral-700">경매에 올릴 판매 중인 상품이 없어요. 상품을 먼저 등록해 주세요.</p>
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
          <span className="text-label font-semibold text-brand-700">경매 진행 중</span>
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

/** 이 방송에서 연 경매: 진행 중인 것을 위에, 끝난 것은 아래에. 현재가·입찰 수·남은 시간과 취소 버튼. */
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
            <li className="rounded-md bg-neutral-25 px-3 py-3 text-label text-neutral-500">경매 상품에서 [경매 시작]을 누르면 여기에서 현재가와 남은 시간을 볼 수 있어요.</li>
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
