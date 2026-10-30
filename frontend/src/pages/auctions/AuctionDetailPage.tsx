import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronRight, Radio } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { api, call, errorMessage, isErrorCode, useSession } from '@/api';
import { AuctionPhaseBadge, AuctionTime } from '@/components/auction/AuctionBits';
import { AuctionAlertButton } from '@/components/auction/AuctionAlertButton';
import { BidDialog } from '@/components/auction/BidDialog';
import { BidHistory } from '@/components/auction/BidHistory';
import { MyBidStatus } from '@/components/auction/MyBidStatus';
import { auctionKey, useAuctionRealtime } from '@/lib/use-auction-realtime';
import { useLoginGuard } from '@/components/auth/use-login-guard';
import { ProductDescription } from '@/components/product/ProductDescription';
import { ProductGallery } from '@/components/product/ProductGallery';
import { SellerIdentity } from '@/components/seller/SellerIdentity';
import { Badge, Button, buttonVariants, Card, CardBody, ConfirmDialog, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { auctionPhase, secondsUntil, type AuctionPhase } from '@/lib/auction';
import { formatDateTime, formatNumber, formatPoint, formatRemaining } from '@/lib/format';
import { useMyBid } from '@/lib/use-my-bid';
import { useNow } from '@/lib/use-now';
import { ProductAuctionHistory } from './ProductAuctionHistory';

/** 시작 전·진행 중인 경매도 이 간격으로 다시 불러온다. (실시간 메시지가 끊겼을 때의 보조) */
const REFRESH_MS = 10_000;

/**
 * 경매 상세: 상품 정보·입찰 기록 + 현재가·입찰 수·남은 시간·내 입찰 상태. 내 경매면 취소할 수 있다.
 * [입찰하기]는 입찰 창(BidDialog)을 연다.
 */
export function AuctionDetailPage() {
  const params = useParams();
  const auctionId = Number(params.auctionId);
  if (!Number.isInteger(auctionId) || auctionId <= 0) return <NotFound />;
  return <AuctionDetail key={auctionId} auctionId={auctionId} />;
}

function AuctionDetail({ auctionId }: { auctionId: number }) {
  const session = useSession();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [confirmCancel, setConfirmCancel] = useState(false);
  // 로그인하지 않아도 경매 정보는 볼 수 있다. 입찰·문의는 로그인 창을 띄우고, 로그인하면 이어서 한다.
  const { guard, loginDialog } = useLoginGuard();
  const [bidOpen, setBidOpen] = useState(false);

  const query = useQuery({
    queryKey: auctionKey(auctionId),
    queryFn: () => call(api.GET('/api/v1/auctions/{auctionId}', { params: { path: { auctionId } } })),
    // 시작 전 경매도 다시 불러온다. 방송에 올라간 경매는 방송이 끝나면 "방송 중 경매"(live) 표시를 내려야 하므로,
    // 경매가 먼저 끝났어도 연결된 방송이 아직 방송 중(live)이면 계속 다시 불러온다.
    refetchInterval: (q) => {
      const data = q.state.data;
      return data?.status === 'ONGOING' || data?.status === 'READY' || data?.live ? REFRESH_MS : false;
    },
  });
  // 현재가·입찰 수·마감 시각은 실시간으로 받는다. (주기적 새로 불러오기는 실시간이 끊겼을 때의 보조)
  useAuctionRealtime(auctionId);
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
      // 판매자에게 보이는 이 상품의 경매 이력도 취소된 상태로 다시 불러온다.
      void queryClient.invalidateQueries({ queryKey: ['product-auctions', productId] });
    },
  });

  const now = useNow(auction?.status === 'ONGOING' ? 1000 : null);
  // 내 경매에는 입찰할 수 없으니 불러오지 않는다.
  const myBid = useMyBid(auctionId, auction !== undefined && auction.sellerId !== session?.user.userId);

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

  const priceLabel = phase === 'ENDED' ? '낙찰가' : phase === 'READY' ? '시작가' : '현재가';
  const result = phase ? RESULT_TEXT[phase] : undefined;

  return (
    <div className="flex items-start gap-8">
      <section aria-label="상품" className="flex min-w-0 flex-1 flex-col gap-5">
        {product.isPending ? (
          <Skeleton className="aspect-[4/3] w-full rounded-lg" />
        ) : product.isError ? (
          // 못 불러온 것을 "이미지 없음"으로 보이지 않게 오류와 다시 시도를 보여 준다.
          <ErrorState message={`상품 이미지를 불러오지 못했어요. ${errorMessage(product.error)}`} onRetry={() => void product.refetch()} />
        ) : (
          <ProductGallery name={auction.productName ?? '상품'} images={product.data?.images} />
        )}
        <Card>
          <CardBody className="flex flex-col gap-3 pt-5">
            <h2 className="text-title-md font-bold">상품 설명</h2>
            {product.isPending ? (
              <Skeleton className="h-16" />
            ) : product.isError ? (
              <Notice tone="error">상품 설명을 불러오지 못했어요. {errorMessage(product.error)}</Notice>
            ) : (
              <>
                {product.data?.description?.trim() ? (
                  <ProductDescription description={product.data.description} images={product.data.descriptionImages} />
                ) : (
                  <p className="text-body-lg leading-relaxed text-neutral-700">판매자가 적은 설명이 없어요.</p>
                )}
                {product.data?.categoryName && (
                  <span className="self-start rounded-full bg-neutral-50 px-3 py-1 text-label font-semibold text-neutral-700">
                    <span className="sr-only">카테고리 </span>
                    {product.data.categoryName}
                  </span>
                )}
              </>
            )}
          </CardBody>
        </Card>
        {/* 시작 전 경매는 입찰이 있을 수 없으니 기록을 보여주지 않는다. */}
        {phase !== 'READY' && phase !== 'CANCELED' && <BidHistory auctionId={auctionId} bidCount={auction.bidCount ?? 0} />}
        {/* 판매자 본인일 때만: 이 상품으로 열었던 경매 전부 (남이면 그리지도, 부르지도 않는다) */}
        {isMine && productId !== undefined && auction.id !== undefined && <ProductAuctionHistory productId={productId} currentAuctionId={auction.id} />}
      </section>

      <aside aria-label="경매 정보" className="sticky top-22 flex w-[440px] shrink-0 flex-col gap-4">
        <div className="flex items-center gap-2">
          <AuctionPhaseBadge phase={phase} />
          {/* 방송에 올라갔어도 방송이 끝났으면 표시하지 않는다. (경매는 방송과 별개로 계속 진행) */}
          {auction.live && (
            <span className="flex items-center gap-1 text-label font-semibold text-brand-700">
              <Radio className="size-3.5" aria-hidden="true" />
              방송 중 경매
            </span>
          )}
        </div>
        <h1 className="text-title-lg font-bold tracking-tight">{auction.productName || '이름 없는 상품'}</h1>
        {/* 인증 판매자면 인증 마크가 붙고, 누르면 판매자 프로필(경매 기록·신뢰도) 창이 열린다. */}
        <SellerIdentity sellerId={auction.sellerId} fallbackName={auction.sellerName} size="sm" detail="판매자" live={auction.live} />

        {/* 가격과 남은 시간을 가장 크게 */}
        <Card>
          <CardBody className="flex flex-col gap-4 pt-5">
            {result && <p className="rounded-md bg-neutral-50 px-3.5 py-2.5 text-body font-semibold text-neutral-900">{result}</p>}
            <div className="flex items-end justify-between gap-4">
              <div className="flex flex-col">
                <span className="text-label text-neutral-500">{priceLabel}</span>
                <span className="num text-display leading-tight font-bold">
                  {/* 낙찰가를 모르면 시작가로 대신하지 않는다. */}
                  {phase === 'ENDED' && auction.currentPrice == null ? '-' : formatPoint(auction.currentPrice ?? auction.startPrice ?? 0)}
                </span>
              </div>
              {phase === 'ONGOING' ? (
                <div className="flex flex-col items-end">
                  <span className="text-label text-neutral-500">남은 시간</span>
                  <span className="num text-price-lg leading-tight font-bold text-brand-700">{formatRemaining(secondsUntil(auction.endAt, now))}</span>
                </div>
              ) : (
                <AuctionTime status={auction.status} startAt={auction.startAt} endAt={auction.endAt} />
              )}
            </div>
            {phase === 'ONGOING' && <TimeProgress startAt={auction.startAt} endAt={auction.endAt} now={now} />}

            {phase === 'CLOSING' && <Notice>마감됐어요. 낙찰 결과를 정리하고 있어요.</Notice>}
            {!isMine && <MyBidStatus myBid={myBid.data} />}
            {!isMine && phase === 'ONGOING' && (
              <Button size="lg" onClick={() => guard(() => setBidOpen(true))}>
                입찰하기
              </Button>
            )}
            {!isMine && phase === 'READY' && <Notice>경매가 시작되면 입찰할 수 있어요.</Notice>}
            {/* 시작 전·진행 중 경매는 알림 받기로 시작·마감 임박·취소·결과를 알림으로 받는다. (내 경매는 결과를 따로 알려 줌) */}
            {!isMine && open && <AuctionAlertButton auctionId={auctionId} requireLogin={guard} />}
          </CardBody>
        </Card>

        {/* 방송에 올라간 경매: 방송 중이면 LIVE 표시와 함께 방송으로 가고, 끝났으면 안내만 남긴다. */}
        {auction.liveStreamId ? (
          auction.live ? (
            <Link
              to={`/lives/${auction.liveStreamId}`}
              className="flex items-center gap-3 rounded-lg border border-brand-200 bg-brand-50 px-4 py-3 font-semibold transition-colors hover:bg-brand-100"
            >
              <Badge variant="live">LIVE</Badge>
              <span className="flex-1">방송 보러 가기</span>
              <ChevronRight className="size-4 text-brand-700" aria-hidden="true" />
            </Link>
          ) : (
            <p className="px-1 text-label text-neutral-500">이 경매가 소개된 방송은 끝났어요.</p>
          )
        ) : null}

        {(!isMine && auction.sellerId) || cancelable ? (
          <div className="flex gap-2">
            {!isMine && auction.sellerId && (
              <Link
                to={`/messages/${auction.sellerId}`}
                onClick={(e) => {
                  if (session) return;
                  e.preventDefault();
                  guard(() => navigate(`/messages/${auction.sellerId}`));
                }}
                className={buttonVariants({ variant: 'outline', className: 'flex-1' })}
              >
                판매자에게 문의
              </Link>
            )}
            {cancelable && (
              <Button variant="outline" className="flex-1" onClick={() => setConfirmCancel(true)}>
                경매 취소
              </Button>
            )}
          </div>
        ) : null}
        {isMine && (auction.bidCount ?? 0) > 0 && open && <span className="text-caption text-neutral-500">입찰이 있는 경매는 취소할 수 없어요.</span>}

        {/* 부가 정보는 작게 */}
        <dl className="grid grid-cols-2 gap-x-6 gap-y-2 border-t border-neutral-100 px-1 pt-4 text-label">
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
      </aside>

      {loginDialog}
      <BidDialog auctionId={bidOpen ? auctionId : undefined} onClose={() => setBidOpen(false)} showDetailLink={false} realtime={false} />
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

/** 끝난 경매는 결과를 가격 위에 한 줄로 크게 알린다. */
const RESULT_TEXT: Partial<Record<AuctionPhase, string>> = {
  ENDED: '낙찰된 경매예요.',
  FAILED: '입찰 없이 끝난 경매예요. (유찰)',
  CANCELED: '판매자가 취소한 경매예요.',
};

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-neutral-500">{label}</dt>
      <dd className="num font-semibold">{children}</dd>
    </div>
  );
}

/** 진행 중 경매의 시간 막대: 시작부터 마감까지 얼마나 지났는지 */
function TimeProgress({ startAt, endAt, now }: { startAt?: string; endAt?: string; now: Date }) {
  if (!startAt || !endAt) return null;
  const start = new Date(startAt).getTime();
  const total = new Date(endAt).getTime() - start;
  const elapsed = now.getTime() - start;
  // 마감 시각 전에는 99%를 넘기지 않는다. (반올림으로 마감 몇 분 전부터 "100% 지남"이 되지 않게)
  const passed = total <= 0 || elapsed >= total ? 100 : Math.min(99, Math.max(0, Math.floor((elapsed / total) * 100)));
  return (
    <div role="img" aria-label={`경매 시간 ${passed}% 지남`} className="h-1.5 overflow-hidden rounded-full bg-neutral-100">
      <div className="h-full rounded-full bg-brand-500" style={{ width: `${passed}%` }} />
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
