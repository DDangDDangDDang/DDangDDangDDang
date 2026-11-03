import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, MessageCircle } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { AddressFields, deliveryFieldsOf, isDeliveryFilled, type DeliveryFields } from '@/components/address/AddressFields';
import { addressListQuery, addressesQueryKey, defaultAddressQuery } from '@/components/address/address-query';
import { OrderStatusBadge, ProductImage } from '@/components/order/OrderBits';
import { Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, ErrorState, Input, Notice, Select, Skeleton, buttonVariants } from '@/components/ui';
import { formatDateTime, formatPhone, formatPoint, onlyDigits } from '@/lib/format';
import { COURIER_LABEL, type Courier } from '@/lib/labels';
import {
  autoConfirmMessage,
  hasDeliveryAddress,
  orderCancelMessage,
  orderDeadlineMessage,
  orderRoleOf,
  orderStage,
  requireOrderId,
  type OrderRole,
} from '@/lib/order-view';
import { OrderDelayCard } from './OrderDelayCard';
import { OrderReviewSection } from './OrderReviewSection';
import { TrackingCard } from './TrackingCard';

type Order = Schema<'OrderResponse'>;

/** 주문 상세. 로그인한 회원이 구매자인지 판매자인지에 따라 다른 내용을 보여준다. */
export function OrderDetailPage() {
  const orderId = Number(useParams().orderId);
  // 주소가 /orders/abc, /orders/0 처럼 잘못되면 서버에 묻지 않고 바로 안내한다.
  const validId = Number.isInteger(orderId) && orderId > 0;
  const session = useSession();

  const query = useQuery({
    queryKey: ['orders', 'detail', orderId],
    queryFn: () => call(api.GET('/api/v1/orders/{orderId}', { params: { path: { orderId } } })),
    enabled: validId,
  });

  const order = query.data;
  const role = order ? orderRoleOf(order, session?.user.userId) : null;
  const deadlineMessage = order ? orderDeadlineMessage(order) : null;

  return (
    <div className="mx-auto max-w-[880px]">
      <div className="mb-4 flex items-center justify-between">
        <Link
          to={role === 'SELLER' ? '/orders?role=SELLER' : '/orders'}
          className="inline-flex items-center gap-1 text-label font-semibold text-neutral-500 hover:text-neutral-900"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          주문 내역
        </Link>
        {order && role && <ContactButton order={order} role={role} />}
      </div>

      {!validId ? (
        <Card>
          <ErrorState message="주문을 찾을 수 없어요." />
        </Card>
      ) : query.isPending ? (
        <div className="flex flex-col gap-4">
          <Skeleton className="h-36" />
          <Skeleton className="h-48" />
        </div>
      ) : query.isError || !order ? (
        <Card>
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        </Card>
      ) : (
        <div className="flex flex-col gap-4">
          <OrderSummary order={order} />
          {deadlineMessage && <Notice tone="info">{deadlineMessage}</Notice>}
          {role && <OrderDelayCard order={order} role={role} />}
          {order.status === 'CANCELED' && <Notice tone="error">{orderCancelMessage(order)}</Notice>}
          {order.status !== 'CANCELED' && role === 'BUYER' && <BuyerPanel order={order} />}
          {order.status !== 'CANCELED' && role === 'SELLER' && (
            <>
              <SettlementSummary order={order} />
              <SellerPanel order={order} />
            </>
          )}
        </div>
      )}
    </div>
  );
}

/**
 * 상대방과 1:1 채팅: 구매자는 판매자에게, 판매자는 구매자에게.
 * 채팅 화면(/messages/:userId)이 방을 만들거나(POST /dm/rooms) 있던 방을 그대로 연다.
 */
function ContactButton({ order, role }: { order: Order; role: OrderRole }) {
  const targetUserId = role === 'BUYER' ? order.sellerId : order.buyerId;
  if (targetUserId == null) return null;
  return (
    <Link to={`/messages/${targetUserId}`} className={buttonVariants({ variant: 'outline', size: 'sm' })}>
      <MessageCircle className="size-4" aria-hidden="true" />
      {role === 'BUYER' ? '판매자에게 문의' : '구매자에게 문의'}
    </Link>
  );
}

function OrderSummary({ order }: { order: Order }) {
  return (
    <Card>
      <CardBody className="flex items-center gap-5 pt-5">
        <ProductImage src={order.productImageUrl} alt={order.productName ?? '상품'} className="size-24" />
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <OrderStatusBadge status={order.status} />
          <h1 className="truncate text-title-lg font-bold">{order.productName ?? '-'}</h1>
          <p className="text-label text-neutral-500">
            주문번호 <span className="num">{order.orderNo ?? '-'}</span>
            {order.createdAt && <> · {formatDateTime(order.createdAt)}</>}
          </p>
        </div>
        <div className="flex flex-col items-end gap-0.5">
          <span className="text-label text-neutral-500">낙찰가</span>
          <span className="num text-price-lg font-bold">{formatPoint(order.finalPrice ?? 0)}</span>
        </div>
      </CardBody>
    </Card>
  );
}

// ---------------------------------------------------------------- 구매자

function BuyerPanel({ order }: { order: Order }) {
  const stage = orderStage(order);
  const [editingAddress, setEditingAddress] = useState(false);
  // 이 화면에서 방금 구매 확정했으면 리뷰 창을 바로 띄운다.
  const [justConfirmed, setJustConfirmed] = useState(false);

  if (stage === 'NEED_ADDRESS' || (stage === 'WAITING_SHIPMENT' && editingAddress)) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>배송지 입력</CardTitle>
        </CardHeader>
        <CardBody>
          <AddressForm order={order} onDone={() => setEditingAddress(false)} onCancel={stage === 'WAITING_SHIPMENT' ? () => setEditingAddress(false) : undefined} />
        </CardBody>
      </Card>
    );
  }

  if (stage === 'WAITING_SHIPMENT') {
    return (
      <Card>
        <CardHeader>
          <CardTitle>배송지</CardTitle>
          <Button variant="outline" size="sm" onClick={() => setEditingAddress(true)}>
            배송지 수정
          </Button>
        </CardHeader>
        <CardBody className="flex flex-col gap-4">
          <AddressView order={order} />
          <Notice tone="info">발송 대기 중이에요. 판매자가 발송하면 송장번호가 표시돼요.</Notice>
        </CardBody>
      </Card>
    );
  }

  if (stage === 'SHIPPING') return <BuyerShippingPanel order={order} onConfirmed={() => setJustConfirmed(true)} />;

  return (
    <Card>
      <CardHeader>
        <CardTitle>배송</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        <ShipmentView order={order} />
        <Notice tone="success">구매 확정 완료</Notice>
        <OrderReviewSection order={order} prompted={justConfirmed} />
      </CardBody>
    </Card>
  );
}

function BuyerShippingPanel({ order, onConfirmed }: { order: Order; onConfirmed: () => void }) {
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const mutation = useMutation({
    mutationFn: () => call(api.PATCH('/api/v1/orders/{orderId}/confirm', { params: { path: { orderId: requireOrderId(order) } } })),
    onSuccess: (data) => {
      onConfirmed();
      queryClient.setQueryData(['orders', 'detail', order.orderId], data);
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      setConfirming(false);
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle>배송 중</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        <ShipmentView order={order} />
        <AddressView order={order} />
        {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
        <div className="flex items-center justify-end gap-4">
          <p className="text-label text-neutral-500">{autoConfirmMessage(order)}</p>
          <Button onClick={() => setConfirming(true)}>구매 확정</Button>
        </div>
      </CardBody>
      <ConfirmDialog
        open={confirming}
        title="구매를 확정할까요?"
        description="확정하면 판매자에게 대금이 지급됩니다. 확정한 뒤에는 되돌릴 수 없어요."
        confirmLabel="구매 확정"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate()}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}

function AddressForm({ order, onDone, onCancel }: { order: Order; onDone: () => void; onCancel?: () => void }) {
  // 배송지를 처음 넣는 주문은 저장된 기본 배송지로 미리 채운다. 이미 넣은 주문을 고칠 때는 그 주문의 배송지로 채운다.
  const prefill = !hasDeliveryAddress(order);
  // 다른 화면·탭에서 기본 배송지를 지우거나 바꿨을 수 있으므로 들어올 때와 창으로 돌아올 때마다 다시 불러온다
  const defaultQuery = useQuery({ ...defaultAddressQuery, enabled: prefill, refetchOnMount: 'always', refetchOnWindowFocus: 'always' });

  // 첫 조회가 끝난 뒤에는 실패 후 다시 불러오는 중(데이터 없이 pending)이어도 폼을 그대로 둔다
  if (prefill && defaultQuery.isPending && !defaultQuery.isFetched) return <Skeleton className="h-56" />;
  // 기본 배송지를 못 불러와도 빈 칸으로 직접 입력할 수 있게 한다
  const initial = prefill ? deliveryFieldsOf(defaultQuery.data) : deliveryFieldsOf(order);
  return <AddressFormBody order={order} initial={initial} refreshing={prefill && defaultQuery.isFetching} onDone={onDone} onCancel={onCancel} />;
}

function sameDelivery(a: DeliveryFields, b: DeliveryFields) {
  return a.receiverName === b.receiverName && a.receiverPhone === b.receiverPhone && a.address === b.address && a.deliveryMemo === b.deliveryMemo;
}

function AddressFormBody({
  order,
  initial,
  refreshing,
  onDone,
  onCancel,
}: {
  order: Order;
  initial: DeliveryFields;
  /** 기본 배송지를 다시 불러오는 중. 손대지 않은 폼은 끝날 때까지 예전 값으로 제출하지 못하게 한다 */
  refreshing: boolean;
  onDone: () => void;
  onCancel?: () => void;
}) {
  const queryClient = useQueryClient();
  const [value, setValue] = useState(initial);
  const [selectedId, setSelectedId] = useState('');
  // 다시 불러온 기본 배송지는 사용자가 아직 칸을 고치거나 저장된 배송지를 고르지 않았을 때만 채운다
  const [touched, setTouched] = useState(false);
  const [lastInitial, setLastInitial] = useState(initial);
  if (!touched && !sameDelivery(lastInitial, initial)) {
    setLastInitial(initial);
    setValue(initial);
  }
  const savedQuery = useQuery({ ...addressListQuery, refetchOnMount: 'always', refetchOnWindowFocus: 'always' });
  const saved = savedQuery.data ?? [];
  // 고른 배송지가 다른 곳에서 지워졌으면 선택만 푼다 (채운 값은 그대로 둔다)
  if (selectedId && savedQuery.isSuccess && !saved.some((a) => String(a.addressId) === selectedId)) setSelectedId('');

  const mutation = useMutation({
    mutationFn: () =>
      call(
        api.PATCH('/api/v1/orders/{orderId}/address', {
          params: { path: { orderId: requireOrderId(order) } },
          body: {
            receiverName: value.receiverName,
            receiverPhone: value.receiverPhone,
            address: value.address,
            deliveryMemo: value.deliveryMemo || undefined,
          },
        }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(['orders', 'detail', order.orderId], data);
      // 첫 주문의 배송지는 서버가 기본 배송지로 저장한다
      void queryClient.invalidateQueries({ queryKey: addressesQueryKey });
      onDone();
    },
  });

  function choose(addressId: string) {
    setSelectedId(addressId);
    const picked = saved.find((a) => String(a.addressId) === addressId);
    if (picked) {
      setTouched(true);
      setValue(deliveryFieldsOf(picked));
    }
  }

  function edit(next: DeliveryFields) {
    setTouched(true);
    setValue(next);
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
      {savedQuery.isError && (
        <div className="col-span-2 flex items-center gap-3">
          <div className="flex-1">
            <Notice tone="error">저장된 배송지를 불러오지 못했어요. 직접 입력하거나 다시 불러와 주세요.</Notice>
          </div>
          <Button variant="outline" size="sm" onClick={() => void savedQuery.refetch()} loading={savedQuery.isFetching}>
            다시 불러오기
          </Button>
        </div>
      )}
      {saved.length > 0 && (
        <div className="col-span-2 flex items-end gap-2">
          <div className="flex-1">
            <Select
              label="배송지 선택"
              options={[
                { value: '', label: '저장된 배송지에서 고르기' },
                ...saved.map((a) => ({
                  value: String(a.addressId ?? ''),
                  label: `${a.isDefault ? '[기본] ' : ''}${a.addressName ?? '배송지'} · ${a.receiverName ?? ''} · ${a.address ?? ''}`,
                })),
              ]}
              value={selectedId}
              onChange={(e) => choose(e.target.value)}
              disabled={mutation.isPending}
            />
          </div>
          <Link to="/me/addresses" className={buttonVariants({ variant: 'ghost' })}>
            배송지 관리
          </Link>
        </div>
      )}
      <AddressFields value={value} onChange={edit} />
      <p className="col-span-2 text-caption text-neutral-500">여기서 고친 내용은 이 주문에만 쓰이고, 저장된 배송지는 바뀌지 않아요.</p>
      {mutation.isError && (
        <div className="col-span-2">
          <Notice tone="error">{errorMessage(mutation.error)}</Notice>
        </div>
      )}
      <div className="col-span-2 flex justify-end gap-2">
        {onCancel && (
          <Button variant="ghost" onClick={onCancel} disabled={mutation.isPending}>
            취소
          </Button>
        )}
        <Button type="submit" loading={mutation.isPending} disabled={!isDeliveryFilled(value) || (refreshing && !touched)}>
          {onCancel ? '배송지 수정' : '배송지 입력'}
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- 판매자

/** 판매자에게만: 판매가에서 판매 수수료를 뺀 정산 금액. 구매 확정 전엔 "받을 금액", 확정 후엔 "받은 금액" */
function SettlementSummary({ order }: { order: Order }) {
  const rate = order.commissionRateBps ?? 0;
  const settled = order.status === 'COMPLETED';
  return (
    <Card>
      <CardBody className="pt-5">
        <dl aria-label="정산 금액" className="grid grid-cols-3 gap-4">
          <div className="flex flex-col">
            <dt className="text-caption text-neutral-500">판매가</dt>
            <dd className="num text-body font-semibold">{formatPoint(order.finalPrice ?? 0)}</dd>
          </div>
          <div className="flex flex-col">
            <dt className="text-caption text-neutral-500">판매 수수료{rate > 0 ? ` ${Number((rate / 100).toFixed(2))}%` : ''}</dt>
            <dd className="num text-body font-semibold">{(order.commission ?? 0) > 0 ? `- ${formatPoint(order.commission ?? 0)}` : '없음'}</dd>
          </div>
          <div className="flex flex-col items-end">
            <dt className="text-caption text-neutral-500">{settled ? '받은 금액' : '받을 금액 (구매 확정 후)'}</dt>
            <dd className="num text-title-md font-bold">{formatPoint(order.settlementAmount ?? order.finalPrice ?? 0)}</dd>
          </div>
        </dl>
      </CardBody>
    </Card>
  );
}

function SellerPanel({ order }: { order: Order }) {
  const stage = orderStage(order);

  if (stage === 'NEED_ADDRESS') {
    return (
      <Card>
        <CardHeader>
          <CardTitle>발송</CardTitle>
        </CardHeader>
        <CardBody className="flex flex-col gap-4">
          <Notice tone="info">구매자의 배송지 입력을 기다리는 중이에요.</Notice>
          <div className="flex justify-end">
            <Button disabled>발송 정보 입력</Button>
          </div>
        </CardBody>
      </Card>
    );
  }

  if (stage === 'WAITING_SHIPMENT') {
    return (
      <Card>
        <CardHeader>
          <CardTitle>발송 정보 입력</CardTitle>
        </CardHeader>
        <CardBody className="flex flex-col gap-5">
          <section aria-label="구매자 배송지" className="rounded-md bg-neutral-25 p-4">
            <AddressView order={order} />
          </section>
          <ShipForm order={order} />
        </CardBody>
      </Card>
    );
  }

  if (stage === 'SHIPPING') {
    return (
      <Card>
        <CardHeader>
          <CardTitle>배송 중</CardTitle>
        </CardHeader>
        <CardBody className="flex flex-col gap-4">
          <ShipmentView order={order} />
          <Notice tone="info">구매 확정 대기 중이에요. {autoConfirmMessage(order)}</Notice>
        </CardBody>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>정산</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        <ShipmentView order={order} />
        <Notice tone="success">정산 완료. 구매자가 확정해 판매 수수료를 뺀 금액이 포인트로 지급됐어요.</Notice>
      </CardBody>
    </Card>
  );
}

const COURIER_OPTIONS = [
  { value: '', label: '택배사를 선택해 주세요' },
  ...(Object.entries(COURIER_LABEL) as [Courier, string][]).map(([value, label]) => ({ value, label })),
];

function ShipForm({ order }: { order: Order }) {
  const queryClient = useQueryClient();
  const [courier, setCourier] = useState<Courier | ''>('');
  const [trackingNo, setTrackingNo] = useState('');

  const mutation = useMutation({
    mutationFn: () =>
      call(
        api.PATCH('/api/v1/orders/{orderId}/ship', {
          params: { path: { orderId: requireOrderId(order) } },
          body: { courier: courier as Courier, trackingNo },
        }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(['orders', 'detail', order.orderId], data);
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
      <Select label="택배사" options={COURIER_OPTIONS} value={courier} onChange={(e) => setCourier(e.target.value as Courier | '')} required />
      <Input
        label="송장번호"
        inputMode="numeric"
        hint="하이픈 없이 숫자만 입력해 주세요."
        value={trackingNo}
        maxLength={20}
        onChange={(e) => setTrackingNo(onlyDigits(e.target.value).slice(0, 20))}
        required
      />
      {mutation.isError && (
        <div className="col-span-2">
          <Notice tone="error">{errorMessage(mutation.error)}</Notice>
        </div>
      )}
      <div className="col-span-2 flex justify-end">
        <Button type="submit" loading={mutation.isPending} disabled={!courier || !trackingNo}>
          발송 정보 입력
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- 공통 표시

function InfoList({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="grid grid-cols-[120px_1fr] gap-y-2.5 text-body">
      {items.map(([label, value]) => (
        <div key={label} className="contents">
          <dt className="text-neutral-500">{label}</dt>
          <dd className="min-w-0 break-words">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function AddressView({ order }: { order: Order }) {
  return (
    <InfoList
      items={[
        ['받는 사람', order.receiverName ?? '-'],
        ['연락처', <span className="num">{order.receiverPhone ? formatPhone(order.receiverPhone) : '-'}</span>],
        ['주소', order.address ?? '-'],
        ['배송 요청사항', order.deliveryMemo || '없음'],
      ]}
    />
  );
}

function ShipmentView({ order }: { order: Order }) {
    return (
        <div className="flex flex-col gap-4">
            <InfoList
                items={[
                    ['택배사', order.courier ? COURIER_LABEL[order.courier] : '-'],
                    ['송장번호', <span className="num">{order.trackingNo ?? '-'}</span>],
                ]}
            />
            <TrackingCard order={order} />
        </div>
    );
}
