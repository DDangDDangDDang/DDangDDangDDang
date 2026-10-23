import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession } from '@/api';
import { Button, buttonVariants, Dialog, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { auctionPhase, formatAmountInput, parseAmount } from '@/lib/auction';
import { BID_STEPS, minimumBid, validateBid } from '@/lib/bid';
import { cn } from '@/lib/cn';
import { formatNumber, formatPoint } from '@/lib/format';
import { auctionKey, useAuctionRealtime } from '@/lib/use-auction-realtime';
import { useNow } from '@/lib/use-now';
import { AuctionPhaseBadge, AuctionTime } from './AuctionBits';

/** 입찰 창이 열려 있는 동안 이 간격으로도 다시 불러온다. (실시간 메시지가 끊겼을 때를 대비한 보조) */
const REFRESH_MS = 10_000;

/**
 * 입찰 창: 방송 시청·경매 상세에서 화면을 옮기지 않고 현재가를 보며 입찰하거나 즉시 구매한다.
 * - 현재가·입찰 수·마감 시각은 실시간(/topic/auctions/{id})으로 바뀐다.
 * - 입찰·즉시 구매는 되돌릴 수 없어 한 번 더 확인한 뒤 보낸다.
 */
/**
 * realtime: 이 창이 직접 경매 실시간 메시지를 받을지. 이미 같은 경매를 구독하는 화면(경매 상세)에서 열면 끈다. (같은 캐시를 함께 씀)
 */
export function BidDialog({
  auctionId,
  onClose,
  showDetailLink = true,
  realtime = true,
}: {
  auctionId?: number;
  onClose: () => void;
  showDetailLink?: boolean;
  realtime?: boolean;
}) {
  return (
    <Dialog open={auctionId !== undefined} onClose={onClose} title="입찰하기" className="w-[480px]">
      {/* 다른 경매를 열면 입력을 처음부터 시작한다. */}
      {auctionId !== undefined && <BidForm key={auctionId} auctionId={auctionId} showDetailLink={showDetailLink} realtime={realtime} />}
    </Dialog>
  );
}

function BidForm({ auctionId, showDetailLink, realtime }: { auctionId: number; showDetailLink: boolean; realtime: boolean }) {
  const session = useSession();
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: auctionKey(auctionId),
    queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } })),
    // 시작 전 경매도 다시 불러와, 창을 연 채로 경매가 시작되면 바로 입찰할 수 있게 한다.
    refetchInterval: (q) => (q.state.data?.status === 'ONGOING' || q.state.data?.status === 'READY' ? REFRESH_MS : false),
  });
  const wallet = useQuery({
    queryKey: ['points', 'wallet'],
    queryFn: () => call(api.GET('/api/v1/points/wallet')),
    enabled: session !== null,
  });
  const now = useNow(1000);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string>();
  // 확인을 기다리는 입찰 금액, 즉시 구매 확인 중인지, 마지막으로 성공한 결과
  const [confirmAmount, setConfirmAmount] = useState<number>();
  const [confirmBuyNow, setConfirmBuyNow] = useState(false);
  const [result, setResult] = useState<{ kind: 'bid'; amount: number; extended: boolean } | { kind: 'buy'; orderId?: number; price: number }>();
  useAuctionRealtime(auctionId, realtime);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: auctionKey(auctionId) });
    void queryClient.invalidateQueries({ queryKey: ['points', 'wallet'] });
  };
  const bid = useMutation({
    mutationFn: (bidAmount: number) =>
      call(api.POST('/api/v1/auctions/{auctionId}/bids', { params: { path: { auctionId } }, body: { amount: bidAmount } })),
    onSuccess: (data, bidAmount) => {
      setResult({ kind: 'bid', amount: data?.amount ?? bidAmount, extended: data?.extended ?? false });
      setAmount('');
    },
    // 성공해도 실패해도 현재가·내 포인트를 다시 불러온다. (그 사이 다른 입찰이 들어왔을 수 있음)
    onSettled: () => {
      setConfirmAmount(undefined);
      refresh();
    },
  });
  const buyNow = useMutation({
    mutationFn: () => call(api.POST('/api/v1/auctions/{auctionId}/buy-now', { params: { path: { auctionId } } })),
    onSuccess: (data) => setResult({ kind: 'buy', orderId: data?.orderId, price: data?.price ?? 0 }),
    onSettled: () => {
      setConfirmBuyNow(false);
      refresh();
    },
  });

  if (query.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-24" />
        <Skeleton className="h-12" />
      </div>
    );
  }
  if (query.isError || !query.data) {
    return <ErrorState message={query.isError ? errorMessage(query.error) : '경매를 찾을 수 없어요.'} onRetry={() => void query.refetch()} />;
  }

  const auction = query.data;
  const phase = auctionPhase(auction, now);
  const ongoing = phase === 'ONGOING';
  const min = minimumBid(auction);
  const unit = auction.bidUnit ?? 0;
  const value = parseAmount(amount);
  const balance = wallet.data?.balance;

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const next = validateBid(amount, auction);
    setError(next);
    if (next || value === undefined) return;
    // 잔액을 확인한 뒤에만 다음 단계로 간다. (아직 불러오는 중이면 기다리고, 불러오지 못했으면 다시 불러온다)
    if (balance === undefined) {
      if (session === null) setError('로그인한 뒤 입찰할 수 있어요.');
      else if (wallet.isError) void wallet.refetch();
      return;
    }
    if (value > balance) {
      setError('사용 가능 포인트가 부족해요. 포인트 화면에서 충전할 수 있어요.');
      return;
    }
    // 되돌릴 수 없으므로 한 번 더 확인한다.
    setResult(undefined);
    bid.reset();
    setConfirmAmount(value);
  };

  return (
    <form onSubmit={onSubmit} noValidate aria-label="입찰" className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <AuctionPhaseBadge phase={phase} />
          <strong className="truncate text-body-lg font-bold">{auction.productName || '이름 없는 상품'}</strong>
        </div>
        <AuctionTime status={auction.status} startAt={auction.startAt} endAt={auction.endAt} />
      </div>

      <dl className="grid grid-cols-3 gap-2 rounded-md bg-neutral-25 px-4 py-3">
        <div className="col-span-3 flex flex-col">
          <dt className="text-caption text-neutral-500">{(auction.bidCount ?? 0) === 0 ? '시작가' : '현재가'}</dt>
          <dd aria-live="polite" className="num text-price-lg font-bold">
            {formatPoint(auction.currentPrice ?? auction.startPrice ?? 0)}
          </dd>
        </div>
        <div className="flex flex-col">
          <dt className="text-caption text-neutral-500">입찰 수</dt>
          <dd className="text-body font-semibold">
            <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
          </dd>
        </div>
        <div className="flex flex-col">
          <dt className="text-caption text-neutral-500">최소 입찰 단위</dt>
          <dd className="num text-body font-semibold">{formatPoint(unit)}</dd>
        </div>
        <div className="flex flex-col">
          <dt className="text-caption text-neutral-500">내 사용 가능 포인트</dt>
          <dd className="num text-body font-semibold">{balance === undefined ? '-' : formatPoint(balance)}</dd>
        </div>
      </dl>

      {/* 즉시 구매가 끝나면 경매 상태가 아직 바뀌지 않았어도 결과만 보여 주고, 더 입찰·구매하지 못하게 한다. */}
      {result?.kind === 'buy' ? (
        <Notice tone="success">
          {formatPoint(result.price)}에 즉시 구매했어요. 낙찰받은 상품은{' '}
          <Link to={result.orderId ? `/orders/${result.orderId}` : '/orders'} target="_blank" rel="noreferrer" className="font-semibold underline">
            주문 내역
          </Link>
          에서 확인하세요.
        </Notice>
      ) : ongoing ? (
        <>
          <div role="group" aria-label="금액 빠르게 정하기" className="grid grid-cols-4 gap-2">
            {BID_STEPS.map((step) => {
              const preset = min + unit * step;
              return (
                <button
                  key={step}
                  type="button"
                  aria-pressed={value === preset}
                  onClick={() => {
                    setAmount(String(preset));
                    setError(undefined);
                    setConfirmAmount(undefined);
                  }}
                  className={cn(
                    'num h-11 rounded-md border text-label font-semibold transition-colors',
                    value === preset ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-neutral-200 hover:bg-neutral-25',
                  )}
                >
                  {step === 0 ? '최소' : `+${formatNumber(unit * step)}`}
                </button>
              );
            })}
          </div>
          <Input
            label="입찰 금액"
            inputMode="numeric"
            suffix="P"
            placeholder={formatNumber(min)}
            value={formatAmountInput(amount)}
            error={error}
            hint={`${formatPoint(min)}부터 입찰할 수 있어요.`}
            onChange={(e) => {
              setAmount(e.target.value);
              setError(undefined);
              setConfirmAmount(undefined);
            }}
          />
          {!error && value !== undefined && balance !== undefined && value > balance && (
            <Notice tone="error">사용 가능 포인트가 부족해요. 포인트 화면에서 충전할 수 있어요.</Notice>
          )}
          {wallet.isError && (
            <Notice tone="error">
              내 포인트를 불러오지 못했어요. {errorMessage(wallet.error)}{' '}
              <button type="button" onClick={() => void wallet.refetch()} className="font-semibold underline">
                다시 불러오기
              </button>
            </Notice>
          )}
          {bid.isError && <Notice tone="error">{errorMessage(bid.error)}</Notice>}
          {result?.kind === 'bid' && (
            <Notice tone="success">
              {formatPoint(result.amount)}로 입찰했어요. 지금 최고 입찰자예요.{result.extended && ' 마감 직전 입찰이라 마감이 30초 늘어났어요.'}
            </Notice>
          )}
          {confirmAmount !== undefined ? (
            <div role="group" aria-label="입찰 확인" className="flex flex-col gap-2 rounded-md border border-brand-200 bg-brand-50 px-4 py-3">
              <p className="text-body">
                <strong className="num">{formatPoint(confirmAmount)}</strong>로 입찰할까요? 입찰은 취소할 수 없어요.
              </p>
              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={() => setConfirmAmount(undefined)} disabled={bid.isPending}>
                  다시 정하기
                </Button>
                <Button size="sm" loading={bid.isPending} onClick={() => bid.mutate(confirmAmount)}>
                  {formatPoint(confirmAmount)} 입찰 확정
                </Button>
              </div>
            </div>
          ) : (
            <Button type="submit" size="lg" loading={session !== null && (wallet.isPending || wallet.isRefetching)}>
              {value ? `${formatPoint(value)} 입찰하기` : '입찰하기'}
            </Button>
          )}
          {auction.buyNowPrice ? (
            <BuyNowSection
              price={auction.buyNowPrice}
              balance={balance}
              confirming={confirmBuyNow}
              pending={buyNow.isPending}
              error={buyNow.isError ? errorMessage(buyNow.error) : undefined}
              onAsk={() => {
                setResult(undefined);
                buyNow.reset();
                setConfirmBuyNow(true);
              }}
              onCancel={() => setConfirmBuyNow(false)}
              onConfirm={() => buyNow.mutate()}
            />
          ) : null}
        </>
      ) : (
        <Notice>{phase === 'READY' ? '경매가 시작되면 입찰할 수 있어요.' : '입찰이 끝난 경매예요.'}</Notice>
      )}

      <BidWarning />
      {/* 경매 상세에서 연 창에는 같은 화면으로 가는 링크를 두지 않는다. */}
      {showDetailLink && (
        <Link to={`/auctions/${auctionId}`} target="_blank" rel="noreferrer" className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'self-center' })}>
          경매 상세 새 탭에서 보기
        </Link>
      )}
    </form>
  );
}

/** 즉시 구매: 즉시구매가로 바로 낙찰받는다. 되돌릴 수 없으므로 한 번 더 확인한다. */
function BuyNowSection({
  price,
  balance,
  confirming,
  pending,
  error,
  onAsk,
  onCancel,
  onConfirm,
}: {
  price: number;
  balance?: number;
  confirming: boolean;
  pending: boolean;
  error?: string;
  onAsk: () => void;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const short = balance !== undefined && price > balance;
  return (
    <div className="flex flex-col gap-2 border-t border-neutral-100 pt-3">
      {confirming ? (
        <div role="group" aria-label="즉시 구매 확인" className="flex flex-col gap-2 rounded-md border border-brand-200 bg-brand-50 px-4 py-3">
          <p className="text-body">
            <strong className="num">{formatPoint(price)}</strong>에 바로 낙찰받을까요? 포인트가 바로 결제되고 되돌릴 수 없어요.
          </p>
          <div className="flex justify-end gap-2">
            <Button variant="outline" size="sm" onClick={onCancel} disabled={pending}>
              취소
            </Button>
            <Button size="sm" loading={pending} onClick={onConfirm}>
              {formatPoint(price)} 즉시 구매 확정
            </Button>
          </div>
        </div>
      ) : (
        <Button variant="outline" onClick={onAsk} disabled={short}>
          즉시 구매 <span className="num">{formatPoint(price)}</span>
        </Button>
      )}
      {short && !confirming && <p className="text-caption text-neutral-500">사용 가능 포인트가 즉시구매가보다 적어요.</p>}
      {error && <Notice tone="error">{error}</Notice>}
    </div>
  );
}

/** 입찰 전에 알아야 할 것. 입찰은 취소할 수 없고, 낙찰되면 묶인 포인트가 결제된다. (서버: 입찰 시 홀딩, 상위 입찰 시 해제, 낙찰 시 차감) */
function BidWarning() {
  return (
    <ul aria-label="입찰 전 확인" className="flex list-disc flex-col gap-0.5 rounded-md bg-neutral-25 py-2.5 pr-3 pl-7 text-caption text-neutral-700">
      <li>입찰은 취소할 수 없어요.</li>
      <li>입찰하면 입찰 금액만큼 포인트가 묶이고, 더 높은 입찰이 들어오면 바로 풀려요.</li>
      <li>
        <strong className="font-semibold text-neutral-900">낙찰되면 묶인 포인트가 결제되고 되돌릴 수 없어요.</strong> 금액을 확인하고 입찰해 주세요.
      </li>
    </ul>
  );
}
