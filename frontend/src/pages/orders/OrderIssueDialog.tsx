import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { Button, Dialog, Notice, Select, Textarea } from '@/components/ui';
import { ORDER_ISSUE_TYPE_LABEL, type OrderIssueType } from '@/lib/labels';
import { requireOrderId } from '@/lib/order-view';

type Order = Schema<'OrderResponse'>;

/** 신고 내용 글자 수 (서버 Order.ISSUE_DETAIL_MAX_LENGTH) */
const DETAIL_MAX = 500;

const TYPE_OPTIONS = (Object.entries(ORDER_ISSUE_TYPE_LABEL) as [OrderIssueType, string][]).map(([value, label]) => ({ value, label }));

/**
 * 구매자의 문제 신고 창. 신고하면 자동 구매 확정이 멈추고 관리자가 확인한 뒤 정산 또는 환불로 처리한다.
 * 판매자에게는 메신저로 신고됐다고 알린다. (신고 내용은 관리자만 본다)
 */
export function OrderIssueDialog({ order, open, onClose }: { order: Order; open: boolean; onClose: () => void }) {
  return (
    <Dialog
      open={open}
      title="문제 신고"
      description="신고하면 자동 구매 확정이 멈추고, 관리자가 확인한 뒤 판매자 정산 또는 전액 환불로 처리해요."
      onClose={onClose}
      className="w-[520px]"
    >
      {/* 창을 열 때마다 새로 그려 이전 입력이 남지 않게 한다. */}
      {open && <OrderIssueForm order={order} onClose={onClose} />}
    </Dialog>
  );
}

function OrderIssueForm({ order, onClose }: { order: Order; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [type, setType] = useState<OrderIssueType>('NOT_RECEIVED');
  const [detail, setDetail] = useState('');
  const mutation = useMutation({
    mutationFn: () =>
      call(
        api.POST('/api/v1/orders/{orderId}/issue', {
          params: { path: { orderId: requireOrderId(order) } },
          body: { type, detail: detail.trim() || undefined },
        }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(['orders', 'detail', order.orderId], data);
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      onClose();
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4">
      <Select label="어떤 문제인가요?" options={TYPE_OPTIONS} value={type} onChange={(e) => setType(e.target.value as OrderIssueType)} />
      <Textarea
        label="자세한 내용 (선택)"
        value={detail}
        maxLength={DETAIL_MAX}
        placeholder="받은 상품의 상태나 배송 상황을 적어 주세요. 관리자가 확인할 때 참고해요."
        hint={`${detail.length}/${DETAIL_MAX}자`}
        onChange={(e) => setDetail(e.target.value)}
      />
      {mutation.isError && <Notice tone="error">{errorMessage(mutation.error)}</Notice>}
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
          닫기
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          신고하기
        </Button>
      </div>
    </form>
  );
}
