import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, useSession } from '@/api';
import { Button, buttonVariants, Dialog, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { auctionPhase, formatAmountInput, parseAmount } from '@/lib/auction';
import { BID_STEPS, minimumBid, validateBid } from '@/lib/bid';
import { cn } from '@/lib/cn';
import { formatNumber, formatPoint } from '@/lib/format';
import { useNow } from '@/lib/use-now';
import { AuctionPhaseBadge, AuctionTime } from './AuctionBits';

/** 입찰 창이 열려 있는 동안 현재가·입찰 수를 이 간격으로 다시 불러온다. (경매 실시간 메시지가 아직 없음) */
const REFRESH_MS = 3000;

/**
 * 입찰 창: 방송 시청·경매 상세에서 화면을 옮기지 않고 현재가를 보며 입찰 금액을 정한다.
 * 입찰 API가 아직 없어 금액 확인까지만 하고, [입찰하기]는 준비 중 안내를 보여준다. (입찰 API가 생기면 onSubmit에 붙인다)
 */
export function BidDialog({ auctionId, onClose }: { auctionId?: number; onClose: () => void }) {
  return (
    <Dialog open={auctionId !== undefined} onClose={onClose} title="입찰하기" className="w-[480px]">
      {/* 다른 경매를 열면 입력을 처음부터 시작한다. */}
      {auctionId !== undefined && <BidForm key={auctionId} auctionId={auctionId} />}
    </Dialog>
  );
}

function BidForm({ auctionId }: { auctionId: number }) {
  const session = useSession();
  const query = useQuery({
    queryKey: ['auction', auctionId],
    queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } })),
    refetchInterval: (q) => (q.state.data?.status === 'ONGOING' ? REFRESH_MS : false),
  });
  const wallet = useQuery({
    queryKey: ['points', 'wallet'],
    queryFn: () => call(api.GET('/api/v1/points/wallet')),
    enabled: session !== null,
  });
  const now = useNow(1000);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string>();
  const [submitted, setSubmitted] = useState(false);

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
    if (next) return;
    setSubmitted(true);
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

      {ongoing ? (
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
                    setSubmitted(false);
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
              setSubmitted(false);
            }}
          />
          {value !== undefined && balance !== undefined && value > balance && (
            <Notice tone="error">사용 가능 포인트가 부족해요. 포인트 화면에서 충전할 수 있어요.</Notice>
          )}
          {submitted && <Notice>입찰 기능을 준비하고 있어요. 곧 이 창에서 바로 입찰할 수 있어요.</Notice>}
          <Button type="submit" size="lg">
            {value ? `${formatPoint(value)} 입찰하기` : '입찰하기'}
          </Button>
        </>
      ) : (
        <Notice>{phase === 'READY' ? '경매가 시작되면 입찰할 수 있어요.' : '입찰이 끝난 경매예요.'}</Notice>
      )}

      <Link to={`/auctions/${auctionId}`} target="_blank" rel="noreferrer" className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'self-center' })}>
        경매 상세 새 탭에서 보기
      </Link>
    </form>
  );
}
