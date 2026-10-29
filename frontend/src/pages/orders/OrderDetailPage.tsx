import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { api, call, errorMessage, useSession, type Schema } from '@/api';
import { OrderStatusBadge, ProductImage } from '@/components/order/OrderBits';
import { Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, ErrorState, Input, Notice, Select, Skeleton } from '@/components/ui';
import { formatDateTime, formatPhone, formatPoint, onlyDigits } from '@/lib/format';
import { COURIER_LABEL, type Courier } from '@/lib/labels';
import { orderRoleOf, orderStage, requireOrderId } from '@/lib/order-view';
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

  return (
    <div className="mx-auto max-w-[880px]">
      <Link
        to={role === 'SELLER' ? '/orders?role=SELLER' : '/orders'}
        className="mb-4 inline-flex items-center gap-1 text-label font-semibold text-neutral-500 hover:text-neutral-900"
      >
        <ArrowLeft className="size-4" aria-hidden="true" />
        주문 내역
      </Link>

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
          {role === 'BUYER' && <BuyerPanel order={order} />}
          {role === 'SELLER' && <SellerPanel order={order} />}
        </div>
      )}
    </div>
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
        <div className="flex justify-end">
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
  const queryClient = useQueryClient();
  const [receiverName, setReceiverName] = useState(order.receiverName ?? '');
  const [receiverPhone, setReceiverPhone] = useState(order.receiverPhone ?? '');
  const [address, setAddress] = useState(order.address ?? '');
  const [deliveryMemo, setDeliveryMemo] = useState(order.deliveryMemo ?? '');

  const mutation = useMutation({
    mutationFn: () =>
      call(
        api.PATCH('/api/v1/orders/{orderId}/address', {
          params: { path: { orderId: requireOrderId(order) } },
          body: { receiverName, receiverPhone, address, deliveryMemo: deliveryMemo || undefined },
        }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(['orders', 'detail', order.orderId], data);
      onDone();
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
      <Input label="받는 사람 이름" value={receiverName} maxLength={20} onChange={(e) => setReceiverName(e.target.value)} required />
      <Input
        label="연락처"
        type="tel"
        inputMode="numeric"
        hint="하이픈 없이 숫자만 입력해 주세요."
        value={receiverPhone}
        onChange={(e) => setReceiverPhone(onlyDigits(e.target.value).slice(0, 11))}
        required
      />
      <div className="col-span-2">
        <Input label="주소" value={address} maxLength={200} onChange={(e) => setAddress(e.target.value)} required />
      </div>
      <div className="col-span-2">
        <Input
          label="배송 요청사항 (선택)"
          placeholder="예: 문 앞에 두고 가 주세요."
          value={deliveryMemo}
          maxLength={100}
          onChange={(e) => setDeliveryMemo(e.target.value)}
        />
      </div>
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
        <Button type="submit" loading={mutation.isPending} disabled={!receiverName || !receiverPhone || !address}>
          {onCancel ? '배송지 수정' : '배송지 입력'}
        </Button>
      </div>
    </form>
  );
}

// ---------------------------------------------------------------- 판매자

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
          <Notice tone="info">구매 확정 대기 중이에요.</Notice>
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
        <Notice tone="success">정산 완료. 구매자가 확정해 판매 금액이 포인트로 지급됐어요.</Notice>
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
