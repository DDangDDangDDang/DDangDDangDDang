import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Package, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { AuctionConditionFields } from '@/components/auction/AuctionConditionFields';
import { PageHeader } from '@/components/layout/PageHeader';
import { ProductFields } from '@/components/product/ProductFields';
import { ProductImageUploadUrlContext, useProductImageUploading } from '@/components/product/use-product-image-upload';
import { SellerAvatar } from '@/components/seller/SellerAvatar';
import { Badge, Button, buttonVariants, Card, Dialog, EmptyState, ErrorState, Input, Notice, Select, Skeleton } from '@/components/ui';
import {
  auctionPhase,
  AUCTION_PHASE_LABEL,
  EMPTY_AUCTION_CONDITION,
  MAX_FEATURED_AUCTIONS,
  toLiveAuctionCreateRequest,
  validateAuctionCondition,
  type AuctionConditionErrors,
  type AuctionConditionValues,
} from '@/lib/auction';
import { formatDateTime, formatPoint, onlyDigits } from '@/lib/format';
import { COURIER_LABEL, ORDER_STATUS_LABEL, PRODUCT_STATUS_LABEL, type Courier } from '@/lib/labels';
import { applyProductChange, productFormValuesOf, toProductRequest, validateProductForm, type ProductFormErrors, type ProductFormValues } from '@/lib/product-form';
import { useCategories } from '@/lib/use-categories';
import { useNow } from '@/lib/use-now';

type LiveBot = Schema<'LiveBotResponse'>;
type Product = Schema<'ProductResponse'>;
type Auction = Schema<'AuctionResponse'>;

const botsKey = ['admin', 'live-bots'] as const;
const sellingKey = (botId: number) => ['admin', 'live-bots', botId, 'selling'] as const;

/**
 * 라이브 봇으로 팔기 (관리자): 봇 이름으로 상품을 등록하고, 켜 둔 봇 방송에서 경매를 시작해 영상 위에 띄우고, 들어온 주문을 발송한다.
 * 규칙(경매 조건·발송 기한·정산)은 사람 판매자와 같다. 정산 금액은 봇 계정 포인트로 들어간다.
 */
export function LiveBotSellingPage() {
  const session = useSession();
  const botId = Number(useParams().botId);
  const isAdmin = session?.user.role === 'ADMIN';

  const bots = useQuery({ queryKey: botsKey, queryFn: () => call(api.GET('/api/v1/admin/live-bots')), enabled: isAdmin });
  const bot = bots.data?.find((item) => item.id === botId);

  if (!isAdmin) return <EmptyState title="관리자만 쓸 수 있는 화면이에요" description="라이브 봇 판매는 관리자가 관리해요." />;
  if (bots.isPending) return <Skeleton className="h-64" />;
  if (bots.isError) return <ErrorState message={errorMessage(bots.error)} onRetry={() => void bots.refetch()} />;
  if (!bot) return <EmptyState title="라이브 봇을 찾을 수 없어요" description="지워졌거나 잘못된 주소예요." />;

  const requestUploadUrl = (body: Schema<'ProductImageUploadRequest'>) =>
    call(api.POST('/api/v1/admin/live-bots/{botId}/products/image-uploads', { params: { path: { botId } }, body }));

  return (
    <ProductImageUploadUrlContext.Provider value={requestUploadUrl}>
      <div className="mx-auto flex max-w-[960px] flex-col gap-6">
        <Link to="/admin/live-bots" className={buttonVariants({ variant: 'ghost', size: 'sm', className: 'self-start' })}>
          <ArrowLeft className="size-4" aria-hidden="true" />
          라이브 봇 목록
        </Link>
        <div className="flex items-center gap-4">
          <SellerAvatar name={bot.nickname} imageUrl={bot.profileImageUrl} size="lg" live={bot.liveStreamId != null} />
          <PageHeader title={`${bot.nickname ?? '봇'} 판매 관리`} description="봇 이름으로 실제 물건을 팔아요. 낙찰되면 운영자가 직접 발송해요." />
        </div>
        <Notice>
          입찰·즉시구매·발송 기한·정산은 일반 판매자와 같아요. 발송 기한 안에 발송하지 않으면 주문이 취소되니 주문을 자주 확인해 주세요. 정산 금액은 봇 계정
          포인트로 들어가요.
        </Notice>
        <ProductsSection bot={bot} />
        <AuctionsSection bot={bot} />
        <OrdersSection bot={bot} />
      </div>
    </ProductImageUploadUrlContext.Provider>
  );
}

/** 상품: 등록하고, 봇 방송 중이면 바로 경매를 시작한다. */
function ProductsSection({ bot }: { bot: LiveBot }) {
  const botId = bot.id ?? 0;
  const live = bot.liveStreamId != null;
  const [creating, setCreating] = useState(false);
  const [auctionFor, setAuctionFor] = useState<Product>();
  const [done, setDone] = useState<string>();

  const products = useQuery({
    queryKey: [...sellingKey(botId), 'products'],
    queryFn: () => call(api.GET('/api/v1/admin/live-bots/{botId}/products', { params: { path: { botId }, query: { page: 0, size: 50 } } })),
  });
  const list = products.data?.content ?? [];

  return (
    <section aria-label="상품" className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-title-md font-bold">상품</h2>
        <Button size="sm" onClick={() => setCreating(true)}>
          <Plus className="size-4" aria-hidden="true" />
          상품 등록
        </Button>
      </div>
      {!live && <Notice>경매는 봇 방송을 켠 뒤에 시작할 수 있어요.</Notice>}
      {done && <Notice tone="success">{done}</Notice>}
      <Card>
        {products.isPending ? (
          <div className="p-4">
            <Skeleton className="h-16" />
          </div>
        ) : products.isError ? (
          <ErrorState message={errorMessage(products.error)} onRetry={() => void products.refetch()} />
        ) : list.length === 0 ? (
          <EmptyState title="등록한 상품이 없어요" description="[상품 등록]으로 봇이 팔 물건을 올려 주세요." />
        ) : (
          <ul className="divide-y divide-neutral-100">
            {list.map((product) => (
              <li key={product.id} className="flex items-center gap-4 px-5 py-3">
                {product.thumbnailUrl ? (
                  <img src={product.thumbnailUrl} alt="" className="size-12 shrink-0 rounded-md object-cover" />
                ) : (
                  <span className="flex size-12 shrink-0 items-center justify-center rounded-md bg-neutral-50 text-neutral-400">
                    <Package className="size-5" aria-hidden="true" />
                  </span>
                )}
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <strong className="truncate text-body font-semibold">{product.name}</strong>
                  <span className="text-caption text-neutral-500">
                    {product.status ? PRODUCT_STATUS_LABEL[product.status] : '-'}
                    {product.createdAt && ` · ${formatDateTime(product.createdAt)} 등록`}
                  </span>
                </div>
                <Button size="sm" variant="outline" disabled={!live || product.status !== 'ACTIVE'} onClick={() => setAuctionFor(product)}>
                  경매 시작
                </Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
      {creating && (
        <ProductCreateDialog
          botId={botId}
          onClose={() => setCreating(false)}
          onCreated={(name) => {
            setCreating(false);
            setDone(`${name} 상품을 등록했어요.`);
          }}
        />
      )}
      {auctionFor && (
        <AuctionStartDialog
          botId={botId}
          product={auctionFor}
          onClose={() => setAuctionFor(undefined)}
          onStarted={() => {
            setAuctionFor(undefined);
            setDone(`${auctionFor.name} 경매를 시작했어요. 아래 진행 중 경매에서 영상 위에 띄울 수 있어요.`);
          }}
        />
      )}
    </section>
  );
}

function ProductCreateDialog({ botId, onClose, onCreated }: { botId: number; onClose: () => void; onCreated: (name: string) => void }) {
  const queryClient = useQueryClient();
  const categories = useCategories();
  const [values, setValues] = useState<ProductFormValues>(() => productFormValuesOf());
  const [errors, setErrors] = useState<ProductFormErrors>({});
  const uploading = useProductImageUploading();

  const create = useMutation({
    mutationFn: () => call(api.POST('/api/v1/admin/live-bots/{botId}/products', { params: { path: { botId } }, body: toProductRequest(values) })),
    onSuccess: async (product) => {
      await queryClient.invalidateQueries({ queryKey: sellingKey(botId) });
      onCreated(product?.name ?? '');
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    // 올리는 중인 이미지는 아직 values에 없어 상품에 붙지 않으므로 끝날 때까지 등록하지 않는다.
    if (uploading) return;
    const next = validateProductForm(values);
    setErrors(next);
    if (Object.keys(next).length === 0) create.mutate();
  };

  return (
    <Dialog open title="상품 등록" description="봇 이름으로 파는 상품이에요." onClose={onClose} closeDisabled={create.isPending} className="max-w-[720px]">
      <form onSubmit={submit} className="flex flex-col gap-4">
        {categories.isPending ? (
          <Skeleton className="h-40" />
        ) : categories.isError ? (
          <ErrorState message={errorMessage(categories.error)} onRetry={() => void categories.refetch()} />
        ) : (
          <ProductFields categories={categories.data ?? []} values={values} errors={errors} onChange={(change) => setValues((prev) => applyProductChange(prev, change))} />
        )}
        {create.isError && <Notice tone="error">{errorMessage(create.error)}</Notice>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={create.isPending}>
            닫기
          </Button>
          <Button type="submit" loading={create.isPending} disabled={uploading}>
            {uploading ? '이미지 올리는 중' : '상품 등록'}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function AuctionStartDialog({ botId, product, onClose, onStarted }: { botId: number; product: Product; onClose: () => void; onStarted: () => void }) {
  const queryClient = useQueryClient();
  const [values, setValues] = useState<AuctionConditionValues>(EMPTY_AUCTION_CONDITION);
  const [errors, setErrors] = useState<AuctionConditionErrors>({});

  const start = useMutation({
    mutationFn: () =>
      call(
        api.POST('/api/v1/admin/live-bots/{botId}/auctions', {
          params: { path: { botId } },
          body: toLiveAuctionCreateRequest(product.id ?? 0, values),
        }),
      ),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: sellingKey(botId) });
      onStarted();
    },
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    const next = validateAuctionCondition(values, 'live', new Date());
    setErrors(next);
    if (Object.keys(next).length === 0) start.mutate();
  };

  return (
    <Dialog open title={`${product.name} 경매 시작`} description="봇 방송에서 바로 시작해요. 방송을 꺼도 경매는 정한 시간까지 이어져요." onClose={onClose} closeDisabled={start.isPending}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <AuctionConditionFields mode="live" values={values} errors={errors} onChange={(patch) => setValues((prev) => ({ ...prev, ...patch }))} compact />
        {start.isError && <Notice tone="error">{errorMessage(start.error)}</Notice>}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={start.isPending}>
            닫기
          </Button>
          <Button type="submit" loading={start.isPending}>
            경매 시작
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

/** 진행 중 경매: 영상 위에 띄우기·내리기, 입찰이 없으면 취소. */
function AuctionsSection({ bot }: { bot: LiveBot }) {
  const botId = bot.id ?? 0;
  const liveStreamId = bot.liveStreamId ?? undefined;
  const queryClient = useQueryClient();
  const now = useNow(1000);
  const [error, setError] = useState<string>();

  const auctions = useQuery({
    queryKey: [...sellingKey(botId), 'auctions'],
    queryFn: () => call(api.GET('/api/v1/admin/live-bots/{botId}/auctions', { params: { path: { botId } } })),
    refetchInterval: 5000,
  });
  // 영상 위에 띄운 경매는 방송 정보에 있다.
  const liveStream = useQuery({
    queryKey: ['live-stream', liveStreamId],
    queryFn: () => call(api.GET('/api/v1/live-streams/{liveStreamId}', { params: { path: { liveStreamId: liveStreamId ?? 0 } } })),
    enabled: liveStreamId !== undefined,
  });
  const featured = liveStream.data?.featuredAuctionIds ?? [];

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: sellingKey(botId) });
    void queryClient.invalidateQueries({ queryKey: ['live-stream', liveStreamId] });
  };
  const toggleFeatured = useMutation({
    mutationFn: (auction: Auction) =>
      featured.includes(auction.id ?? 0)
        ? call(api.DELETE('/api/v1/admin/live-bots/{botId}/featured-auctions/{auctionId}', { params: { path: { botId, auctionId: auction.id ?? 0 } } }))
        : call(api.PUT('/api/v1/admin/live-bots/{botId}/featured-auctions', { params: { path: { botId } }, body: { auctionId: auction.id ?? 0 } })),
    onMutate: () => setError(undefined),
    onError: (e) => setError(errorMessage(e)),
    onSettled: refresh,
  });
  const cancel = useMutation({
    mutationFn: (auction: Auction) => call(api.POST('/api/v1/admin/live-bots/{botId}/auctions/{auctionId}/cancel', { params: { path: { botId, auctionId: auction.id ?? 0 } } })),
    onMutate: () => setError(undefined),
    onError: (e) => setError(errorMessage(e)),
    onSettled: refresh,
  });

  const list = auctions.data ?? [];
  return (
    <section aria-label="진행 중 경매" className="flex flex-col gap-3">
      <h2 className="text-title-md font-bold">진행 중 경매</h2>
      <p className="text-label text-neutral-500">영상 위에는 경매를 {MAX_FEATURED_AUCTIONS}개까지 띄울 수 있어요. 띄운 경매는 시청자가 바로 눌러 입찰해요.</p>
      {error && <Notice tone="error">{error}</Notice>}
      <Card>
        {auctions.isPending ? (
          <div className="p-4">
            <Skeleton className="h-16" />
          </div>
        ) : auctions.isError ? (
          <ErrorState message={errorMessage(auctions.error)} onRetry={() => void auctions.refetch()} />
        ) : list.length === 0 ? (
          <EmptyState title="진행 중인 경매가 없어요" description="상품에서 [경매 시작]을 누르면 여기에 나와요." />
        ) : (
          <ul aria-live="polite" className="divide-y divide-neutral-100">
            {list.map((auction) => {
              const phase = auctionPhase(auction, now);
              const isFeatured = featured.includes(auction.id ?? 0);
              return (
                <li key={auction.id} className="flex items-center gap-4 px-5 py-3">
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <span className="flex items-center gap-2">
                      <strong className="truncate text-body font-semibold">{auction.productName}</strong>
                      {phase && <Badge variant={phase === 'ONGOING' ? 'live' : 'neutral'}>{AUCTION_PHASE_LABEL[phase]}</Badge>}
                      {isFeatured && <Badge variant="info">영상 위</Badge>}
                    </span>
                    <span className="text-caption text-neutral-500">
                      현재가 <span className="num">{formatPoint(auction.currentPrice ?? auction.startPrice ?? auction.buyNowPrice ?? 0)}</span> · 입찰{' '}
                      <span className="num">{auction.bidCount ?? 0}</span>건{auction.endAt && ` · ${formatDateTime(auction.endAt)} 마감`}
                    </span>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={liveStreamId === undefined}
                      loading={toggleFeatured.isPending && toggleFeatured.variables?.id === auction.id}
                      onClick={() => toggleFeatured.mutate(auction)}
                    >
                      {isFeatured ? '영상에서 내리기' : '영상 위에 띄우기'}
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      disabled={(auction.bidCount ?? 0) > 0}
                      title={(auction.bidCount ?? 0) > 0 ? '입찰이 있는 경매는 취소할 수 없어요' : undefined}
                      loading={cancel.isPending && cancel.variables?.id === auction.id}
                      onClick={() => cancel.mutate(auction)}
                    >
                      취소
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </Card>
    </section>
  );
}

const COURIER_OPTIONS = [
  { value: '', label: '택배사를 선택해 주세요' },
  ...(Object.entries(COURIER_LABEL) as [Courier, string][]).map(([value, label]) => ({ value, label })),
];

/** 주문: 봇이 판 주문. 눌러서 배송지를 보고 발송 정보를 넣는다. */
function OrdersSection({ bot }: { bot: LiveBot }) {
  const botId = bot.id ?? 0;
  const [openId, setOpenId] = useState<number>();
  const orders = useQuery({
    queryKey: [...sellingKey(botId), 'orders'],
    queryFn: () => call(api.GET('/api/v1/admin/live-bots/{botId}/orders', { params: { path: { botId }, query: { size: 50 } } })),
    refetchInterval: 30_000,
  });
  const list = orders.data?.content ?? [];

  return (
    <section aria-label="주문" className="flex flex-col gap-3">
      <h2 className="text-title-md font-bold">주문</h2>
      <Card>
        {orders.isPending ? (
          <div className="p-4">
            <Skeleton className="h-16" />
          </div>
        ) : orders.isError ? (
          <ErrorState message={errorMessage(orders.error)} onRetry={() => void orders.refetch()} />
        ) : list.length === 0 ? (
          <EmptyState title="아직 들어온 주문이 없어요" description="봇 경매가 낙찰되거나 즉시구매되면 여기에 나와요." />
        ) : (
          <ul className="divide-y divide-neutral-100">
            {list.map((order) => (
              <li key={order.orderId}>
                <button type="button" onClick={() => setOpenId(order.orderId)} className="flex w-full items-center gap-4 px-5 py-3 text-left hover:bg-neutral-50">
                  <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                    <strong className="truncate text-body font-semibold">{order.productName}</strong>
                    <span className="text-caption text-neutral-500">
                      <span className="num">{formatPoint(order.finalPrice ?? 0)}</span>
                      {order.createdAt && ` · ${formatDateTime(order.createdAt)}`}
                    </span>
                  </div>
                  {order.status && <Badge variant={order.status === 'ESCROW' ? 'warning' : 'neutral'}>{ORDER_STATUS_LABEL[order.status]}</Badge>}
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>
      {openId !== undefined && <OrderDialog botId={botId} orderId={openId} onClose={() => setOpenId(undefined)} />}
    </section>
  );
}

function OrderDialog({ botId, orderId, onClose }: { botId: number; orderId: number; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [courier, setCourier] = useState<Courier | ''>('');
  const [trackingNo, setTrackingNo] = useState('');
  const key = [...sellingKey(botId), 'order', orderId];
  const order = useQuery({
    queryKey: key,
    queryFn: () => call(api.GET('/api/v1/admin/live-bots/{botId}/orders/{orderId}', { params: { path: { botId, orderId } } })),
  });
  const ship = useMutation({
    mutationFn: () =>
      call(
        api.PATCH('/api/v1/admin/live-bots/{botId}/orders/{orderId}/ship', {
          params: { path: { botId, orderId } },
          body: { courier: courier as Courier, trackingNo },
        }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(key, data);
      void queryClient.invalidateQueries({ queryKey: sellingKey(botId) });
    },
  });

  const data = order.data;
  // 배송지가 들어온 발송 전 주문만 발송 정보를 넣을 수 있다.
  const canShip = data?.status === 'ESCROW' && !!data.address;

  return (
    <Dialog open title="주문 상세" description={data?.orderNo ? `주문번호 ${data.orderNo}` : undefined} onClose={onClose} closeDisabled={ship.isPending}>
      {order.isPending ? (
        <Skeleton className="h-40" />
      ) : order.isError || !data ? (
        <ErrorState message={errorMessage(order.error)} onRetry={() => void order.refetch()} />
      ) : (
        <div className="flex flex-col gap-4">
          <dl className="grid grid-cols-[96px_1fr] gap-x-3 gap-y-1.5 text-body">
            <dt className="text-neutral-500">상품</dt>
            <dd>{data.productName}</dd>
            <dt className="text-neutral-500">판매가</dt>
            <dd className="num">{formatPoint(data.finalPrice ?? 0)}</dd>
            <dt className="text-neutral-500">정산 예정</dt>
            <dd className="num">{formatPoint(data.settlementAmount ?? 0)}</dd>
            <dt className="text-neutral-500">상태</dt>
            <dd>{data.status ? ORDER_STATUS_LABEL[data.status] : '-'}</dd>
            <dt className="text-neutral-500">받는 사람</dt>
            <dd>{data.receiverName ?? '아직 배송지를 입력하지 않았어요'}</dd>
            {data.receiverPhone && (
              <>
                <dt className="text-neutral-500">연락처</dt>
                <dd className="num">{data.receiverPhone}</dd>
              </>
            )}
            {data.address && (
              <>
                <dt className="text-neutral-500">주소</dt>
                <dd>{data.address}</dd>
              </>
            )}
            {data.deliveryMemo && (
              <>
                <dt className="text-neutral-500">배송 메모</dt>
                <dd>{data.deliveryMemo}</dd>
              </>
            )}
            {data.shipDeadlineAt && (
              <>
                <dt className="text-neutral-500">발송 기한</dt>
                <dd>{formatDateTime(data.shipDeadlineAt)}</dd>
              </>
            )}
            {data.courier && (
              <>
                <dt className="text-neutral-500">발송</dt>
                <dd>
                  {COURIER_LABEL[data.courier]} <span className="num">{data.trackingNo}</span>
                </dd>
              </>
            )}
          </dl>
          {canShip && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                ship.mutate();
              }}
              className="grid grid-cols-2 gap-3"
            >
              <Select label="택배사" options={COURIER_OPTIONS} value={courier} onChange={(e) => setCourier(e.target.value as Courier | '')} required />
              <Input
                label="송장번호"
                inputMode="numeric"
                hint="하이픈 없이 숫자만"
                value={trackingNo}
                maxLength={20}
                onChange={(e) => setTrackingNo(onlyDigits(e.target.value).slice(0, 20))}
                required
              />
              {ship.isError && (
                <div className="col-span-2">
                  <Notice tone="error">{errorMessage(ship.error)}</Notice>
                </div>
              )}
              <div className="col-span-2 flex justify-end">
                <Button type="submit" loading={ship.isPending} disabled={!courier || !trackingNo}>
                  발송 정보 입력
                </Button>
              </div>
            </form>
          )}
          {ship.isSuccess && <Notice tone="success">발송 정보를 넣었어요. 구매자에게 배송 중으로 보여요.</Notice>}
        </div>
      )}
    </Dialog>
  );
}
