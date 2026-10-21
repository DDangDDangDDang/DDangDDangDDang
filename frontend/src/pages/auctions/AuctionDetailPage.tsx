import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Radio } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { api, call, errorMessage, isErrorCode, useSession } from '@/api';
import { AuctionPhaseBadge, AuctionTime } from '@/components/auction/AuctionBits';
import { ProductImage } from '@/components/order/OrderBits';
import { Button, buttonVariants, Card, CardBody, ConfirmDialog, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { auctionPhase } from '@/lib/auction';
import { formatDateTime, formatNumber, formatPoint } from '@/lib/format';
import { useNow } from '@/lib/use-now';

/** 진행 중인 경매는 현재가·입찰 수를 이 간격으로 다시 불러온다. (경매 실시간 메시지가 아직 없음) */
const REFRESH_MS = 5000;

/** 경매 상세: 상품 정보 + 현재가·입찰 수·남은 시간. 내 경매면 취소할 수 있다. 입찰은 입찰 API가 생기면 이 화면에 붙인다. */
export function AuctionDetailPage() {
  const params = useParams();
  const auctionId = Number(params.auctionId);
  if (!Number.isInteger(auctionId) || auctionId <= 0) return <NotFound />;
  return <AuctionDetail key={auctionId} auctionId={auctionId} />;
}

function AuctionDetail({ auctionId }: { auctionId: number }) {
  const session = useSession();
  const queryClient = useQueryClient();
  const [confirmCancel, setConfirmCancel] = useState(false);

  const query = useQuery({
    queryKey: ['auction', auctionId],
    queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } })),
    refetchInterval: (q) => (q.state.data?.status === 'ONGOING' ? REFRESH_MS : false),
  });
  const auction = query.data;
  const productId = auction?.productId;
  const product = useQuery({
    queryKey: ['product', productId],
    queryFn: () => call(api.GET('/api/v1/products/{productId}', { params: { path: { productId: productId ?? 0 } } })),
    enabled: productId !== undefined,
  });

  const cancel = useMutation({
    mutationFn: () => call(api.POST('/api/v1/auctions/{auctionId}/cancel', { params: { path: { auctionId } } })),
    onSuccess: () => {
      setConfirmCancel(false);
      void queryClient.invalidateQueries({ queryKey: ['auction', auctionId] });
      void queryClient.invalidateQueries({ queryKey: ['auctions'] });
    },
  });

  const now = useNow(auction?.status === 'ONGOING' ? 1000 : null);

  if (query.isPending) return <DetailSkeleton />;
  if (query.isError) {
    if (isErrorCode(query.error, 'AUCTION_001')) return <NotFound />;
    return <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />;
  }
  if (!auction) return <NotFound />;

  const phase = auctionPhase(auction, now);
  const isMine = session?.user.userId !== undefined && session.user.userId === auction.sellerId;
  // 마감 시각이 지난 경매(CLOSING)는 서버 상태가 ONGOING이어도 끝난 것으로 본다. (스튜디오와 같은 기준)
  const open = phase === 'READY' || phase === 'ONGOING';
  const cancelable = isMine && open && (auction.bidCount ?? 0) === 0;

  return (
    <div className="flex items-start gap-8">
      <section aria-label="상품" className="flex min-w-0 flex-1 flex-col gap-5">
        <ProductImage alt={auction.productName ?? '상품'} className="aspect-video w-full rounded-lg" />
        <Card>
          <CardBody className="flex flex-col gap-2 pt-5">
            <h2 className="text-title-md font-bold">상품 설명</h2>
            {product.isPending ? (
              <Skeleton className="h-16" />
            ) : product.isError ? (
              <Notice tone="error">상품 설명을 불러오지 못했어요. {errorMessage(product.error)}</Notice>
            ) : (
              <>
                {product.data?.categoryName && <span className="text-label text-neutral-500">{product.data.categoryName}</span>}
                <p className="text-body whitespace-pre-line text-neutral-700">{product.data?.description || '판매자가 적은 설명이 없어요.'}</p>
              </>
            )}
          </CardBody>
        </Card>
      </section>

      <aside aria-label="경매 정보" className="flex w-[420px] shrink-0 flex-col gap-4">
        <Card>
          <CardBody className="flex flex-col gap-4 pt-5">
            <div className="flex items-center gap-2">
              <AuctionPhaseBadge phase={phase} />
              {auction.liveStreamId && (
                <span className="flex items-center gap-1 text-label font-semibold text-brand-700">
                  <Radio className="size-3.5" aria-hidden="true" />
                  방송 중 경매
                </span>
              )}
            </div>
            <div className="flex flex-col gap-1">
              <h1 className="text-title-lg font-bold tracking-tight">{auction.productName || '이름 없는 상품'}</h1>
              <span className="text-label text-neutral-500">판매자 {auction.sellerName || '-'}</span>
            </div>

            <div className="flex flex-col gap-1 rounded-md bg-neutral-25 px-4 py-3">
              <span className="text-label text-neutral-500">{phase === 'ENDED' ? '낙찰가' : '현재가'}</span>
              <span className="num text-price-lg font-bold">{formatPoint(auction.currentPrice ?? auction.startPrice ?? 0)}</span>
              <AuctionTime status={auction.status} startAt={auction.startAt} endAt={auction.endAt} />
            </div>

            <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-body">
              <Row label="입찰 수">
                <span className="num">{formatNumber(auction.bidCount ?? 0)}</span>회
              </Row>
              <Row label="시작가">{formatPoint(auction.startPrice ?? 0)}</Row>
              <Row label="최소 입찰 단위">{formatPoint(auction.bidUnit ?? 0)}</Row>
              <Row label="즉시구매가">{auction.buyNowPrice ? formatPoint(auction.buyNowPrice) : '없음'}</Row>
              <Row label="시작">{auction.startAt ? formatDateTime(auction.startAt) : '-'}</Row>
              <Row label="마감">{auction.endAt ? formatDateTime(auction.endAt) : '-'}</Row>
              {auction.canceledAt && <Row label="취소">{formatDateTime(auction.canceledAt)}</Row>}
            </dl>

            {phase === 'CLOSING' && <Notice>마감됐어요. 낙찰 결과를 정리하고 있어요.</Notice>}
            {!isMine && (phase === 'ONGOING' || phase === 'READY') && <Notice>입찰 기능을 준비하고 있어요. 곧 이 화면에서 바로 입찰할 수 있어요.</Notice>}

            {auction.liveStreamId && (
              <Link to={`/lives/${auction.liveStreamId}`} className={buttonVariants({ variant: 'primary', size: 'lg' })}>
                방송 보러 가기
              </Link>
            )}
            {cancelable && (
              <Button variant="outline" onClick={() => setConfirmCancel(true)}>
                경매 취소
              </Button>
            )}
            {isMine && (auction.bidCount ?? 0) > 0 && open && (
              <span className="text-caption text-neutral-500">입찰이 있는 경매는 취소할 수 없어요.</span>
            )}
          </CardBody>
        </Card>
      </aside>

      <ConfirmDialog
        open={confirmCancel}
        title="경매를 취소할까요?"
        description={
          <div className="flex flex-col gap-2">
            <p>취소한 경매는 되돌릴 수 없어요. 상품은 다시 경매에 올릴 수 있어요.</p>
            {cancel.isError && <Notice tone="error">{errorMessage(cancel.error)}</Notice>}
          </div>
        }
        confirmLabel="경매 취소"
        cancelLabel="닫기"
        loading={cancel.isPending}
        onConfirm={() => cancel.mutate()}
        onCancel={() => {
          setConfirmCancel(false);
          cancel.reset();
        }}
      />
    </div>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col">
      <dt className="text-caption text-neutral-500">{label}</dt>
      <dd className="num font-semibold">{children}</dd>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div className="flex items-start gap-8" aria-busy="true">
      <div className="flex min-w-0 flex-1 flex-col gap-5">
        <Skeleton className="aspect-video rounded-lg" />
        <Skeleton className="h-28 rounded-lg" />
      </div>
      <Skeleton className="h-[480px] w-[420px] shrink-0 rounded-lg" />
    </div>
  );
}

function NotFound() {
  return (
    <EmptyState
      title="경매를 찾을 수 없어요"
      description="주소가 바뀌었거나 없는 경매예요."
      action={
        <Link to="/auctions" className={buttonVariants({ variant: 'outline', size: 'sm' })}>
          경매 목록으로
        </Link>
      }
    />
  );
}
