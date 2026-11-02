import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { Button, Card, CardBody, CardHeader, CardTitle, Input, Notice } from '@/components/ui';
import { hasDeliveryAddress, requireOrderId, type OrderRole } from '@/lib/order-view';

type Order = Schema<'OrderResponse'>;

/**
 * 발송 지연 신청 카드. 배송지가 입력된 발송 전 주문에서만 보인다.
 * 판매자는 주문당 한 번 신청하고, 구매자는 발송 기한 전까지 승인·거절한다. 응답하지 않으면 기존 기한대로 진행된다.
 */
export function OrderDelayCard({ order, role }: { order: Order; role: OrderRole }) {
  if (order.status !== 'ESCROW' || !hasDeliveryAddress(order)) return null;
  if (!order.delayStatus && role === 'BUYER') return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>발송 지연</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        {!order.delayStatus && <DelayRequestForm order={order} />}
        {order.delayStatus === 'PENDING' && <PendingDelay order={order} role={role} />}
        {order.delayStatus === 'APPROVED' && <Notice tone="success">지연이 승인되어 발송 기한이 7일 늘어났어요.</Notice>}
        {order.delayStatus === 'REJECTED' && <Notice tone="info">지연이 거절되어 기존 발송 기한대로 진행돼요.</Notice>}
      </CardBody>
    </Card>
  );
}

function DelayRequestForm({ order }: { order: Order }) {
  const [reason, setReason] = useState('');
  const onSaved = useOrderSaved(order);
  const mutation = useMutation({
    mutationFn: () =>
      call(
        api.POST('/api/v1/orders/{orderId}/delay-request', {
          params: { path: { orderId: requireOrderId(order) } },
          body: { reason },
        }),
      ),
    onSuccess: onSaved,
  });

  return (
    <>
      <Input
        label="지연 사유"
        hint="주문당 한 번만 신청할 수 있고, 구매자에게 메신저로 전달돼요."
        value={reason}
        maxLength={200}
        onChange={(e) => setReason(e.target.value)}
      />
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end">
        <Button variant="outline" loading={mutation.isPending} disabled={!reason.trim()} onClick={() => mutation.mutate()}>
          지연 신청
        </Button>
      </div>
    </>
  );
}

function PendingDelay({ order, role }: { order: Order; role: OrderRole }) {
  const onSaved = useOrderSaved(order);
  const params = { params: { path: { orderId: requireOrderId(order) } } };
  const mutation = useMutation({
    mutationFn: (approved: boolean) =>
      call(
        approved
          ? api.POST('/api/v1/orders/{orderId}/delay-request/approve', params)
          : api.POST('/api/v1/orders/{orderId}/delay-request/reject', params),
      ),
    onSuccess: onSaved,
  });

  return (
    <>
      <p className="text-body text-neutral-700">사유: {order.delayReason}</p>
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      {role === 'SELLER' ? (
        <Notice tone="info">구매자 응답 대기 중</Notice>
      ) : (
        <>
          <Notice tone="info">승인하면 발송 기한이 7일 늘어나요. 응답하지 않으면 기존 기한대로 진행돼요.</Notice>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" disabled={mutation.isPending} onClick={() => mutation.mutate(false)}>
              거절
            </Button>
            <Button loading={mutation.isPending} onClick={() => mutation.mutate(true)}>
              승인
            </Button>
          </div>
        </>
      )}
    </>
  );
}

function useOrderSaved(order: Order) {
  const queryClient = useQueryClient();
  return (data: Order) => {
    queryClient.setQueryData(['orders', 'detail', order.orderId], data);
    void queryClient.invalidateQueries({ queryKey: ['orders'] });
  };
}
