import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { MapPin, Plus } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { AddressFields, deliveryFieldsOf, EMPTY_DELIVERY, isDeliveryFilled } from '@/components/address/AddressFields';
import { addressListQuery, addressesQueryKey } from '@/components/address/address-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { Badge, Button, Card, CardBody, ConfirmDialog, Dialog, EmptyState, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { formatPhone } from '@/lib/format';

type Address = Schema<'UserAddressResponse'>;

/** 서버가 저장을 막는 개수 (ADDRESS_003). 안내 문구에만 쓴다 */
const MAX_ADDRESSES = 10;

/**
 * 배송지 관리 (/me/addresses): 저장된 배송지 목록, 추가·수정·삭제, 기본 배송지 지정.
 * 기본 배송지는 주문서 배송지 입력에 미리 채워진다. 첫 주문 때 입력한 주소는 서버가 기본 배송지로 저장한다.
 */
export function AddressesPage() {
  const queryClient = useQueryClient();
  const query = useQuery(addressListQuery);
  const addresses = query.data ?? [];

  // 창: 추가(null) 또는 수정(배송지). undefined면 닫힘
  const [editing, setEditing] = useState<Address | null>();
  const [deleting, setDeleting] = useState<Address>();

  const refresh = () => queryClient.invalidateQueries({ queryKey: addressesQueryKey });

  const makeDefault = useMutation({
    mutationFn: (addressId: number) => call(api.PATCH('/api/v1/users/me/addresses/{addressId}/default', { params: { path: { addressId } } })),
    onSuccess: refresh,
  });

  const remove = useMutation({
    mutationFn: (addressId: number) => call(api.DELETE('/api/v1/users/me/addresses/{addressId}', { params: { path: { addressId } } })),
    onSuccess: async () => {
      await refresh();
      setDeleting(undefined);
    },
  });

  // 한 번에 하나의 요청만: 기본 지정·삭제 중에는 목록의 버튼을 모두 막는다 (연타 방지)
  const busy = makeDefault.isPending || remove.isPending;
  const listError = makeDefault.error ?? null;

  return (
    <div className="mx-auto max-w-[720px]">
      <PageHeader
        title="배송지 관리"
        description={`주문할 때 불러와 쓸 배송지예요. 최대 ${MAX_ADDRESSES}개까지 저장할 수 있어요.`}
        actions={
          <Button onClick={() => setEditing(null)} disabled={busy || query.isPending}>
            <Plus className="size-4" aria-hidden="true" />
            배송지 추가
          </Button>
        }
      />

      {query.isPending ? (
        <div className="flex flex-col gap-3">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-28" />
          ))}
        </div>
      ) : query.isError ? (
        <Card>
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        </Card>
      ) : addresses.length === 0 ? (
        <Card>
          <EmptyState
            title="저장된 배송지가 없어요"
            description="배송지를 추가하거나, 첫 주문 때 배송지를 입력하면 기본 배송지로 저장돼요."
            action={<Button onClick={() => setEditing(null)}>배송지 추가</Button>}
          />
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          {listError && <Notice tone="error">{errorMessage(listError)}</Notice>}
          <ul className="flex flex-col gap-3" aria-label="저장된 배송지">
            {addresses.map((item) => (
              <li key={item.addressId}>
                <AddressCard
                  address={item}
                  busy={busy}
                  settingDefault={makeDefault.isPending && makeDefault.variables === item.addressId}
                  onEdit={() => setEditing(item)}
                  onDelete={() => {
                    remove.reset();
                    setDeleting(item);
                  }}
                  onMakeDefault={() => item.addressId != null && makeDefault.mutate(item.addressId)}
                />
              </li>
            ))}
          </ul>
        </div>
      )}

      {editing !== undefined && <AddressDialog address={editing} onClose={() => setEditing(undefined)} onSaved={refresh} />}

      <ConfirmDialog
        open={deleting !== undefined}
        title="배송지를 삭제할까요?"
        description={
          <>
            {deleting?.isDefault && <p>기본 배송지를 지우면 가장 최근에 저장한 배송지가 기본 배송지가 돼요.</p>}
            {remove.isError && <p className="text-red-600">{errorMessage(remove.error)}</p>}
          </>
        }
        confirmLabel="삭제"
        loading={remove.isPending}
        onConfirm={() => deleting?.addressId != null && remove.mutate(deleting.addressId)}
        onCancel={() => setDeleting(undefined)}
      />
    </div>
  );
}

function AddressCard({
  address,
  busy,
  settingDefault,
  onEdit,
  onDelete,
  onMakeDefault,
}: {
  address: Address;
  busy: boolean;
  settingDefault: boolean;
  onEdit: () => void;
  onDelete: () => void;
  onMakeDefault: () => void;
}) {
  return (
    <Card>
      <CardBody className="flex items-start gap-4 pt-5">
        <MapPin className="mt-0.5 size-5 shrink-0 text-neutral-500" aria-hidden="true" />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="text-body-lg font-bold">{address.addressName ?? '배송지'}</span>
            {address.isDefault && <Badge variant="info">기본 배송지</Badge>}
          </div>
          <p className="text-body">
            {address.receiverName ?? '-'} · <span className="num">{address.receiverPhone ? formatPhone(address.receiverPhone) : '-'}</span>
          </p>
          <p className="text-body break-words text-neutral-700">{address.address ?? '-'}</p>
          {address.deliveryMemo && <p className="text-label text-neutral-500">요청사항: {address.deliveryMemo}</p>}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {!address.isDefault && (
            <Button variant="outline" size="sm" onClick={onMakeDefault} disabled={busy} loading={settingDefault}>
              기본으로 지정
            </Button>
          )}
          <Button variant="outline" size="sm" onClick={onEdit} disabled={busy} aria-label={`${address.addressName ?? '배송지'} 수정`}>
            수정
          </Button>
          <Button variant="ghost" size="sm" onClick={onDelete} disabled={busy} aria-label={`${address.addressName ?? '배송지'} 삭제`}>
            삭제
          </Button>
        </div>
      </CardBody>
    </Card>
  );
}

/** 배송지 추가(address = null)·수정 창 */
function AddressDialog({ address, onClose, onSaved }: { address: Address | null; onClose: () => void; onSaved: () => Promise<unknown> }) {
  const [addressName, setAddressName] = useState(address?.addressName ?? '');
  const [fields, setFields] = useState(address ? deliveryFieldsOf(address) : EMPTY_DELIVERY);

  const mutation = useMutation({
    mutationFn: () => {
      const body = {
        addressName: addressName.trim(),
        receiverName: fields.receiverName.trim(),
        receiverPhone: fields.receiverPhone,
        address: fields.address.trim(),
        deliveryMemo: fields.deliveryMemo.trim() || undefined,
      };
      return address?.addressId != null
        ? call(api.PATCH('/api/v1/users/me/addresses/{addressId}', { params: { path: { addressId: address.addressId } }, body }))
        : call(api.POST('/api/v1/users/me/addresses', { body }));
    },
    onSuccess: async () => {
      await onSaved();
      onClose();
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <Dialog open title={address ? '배송지 수정' : '배송지 추가'} onClose={onClose} closeDisabled={mutation.isPending} className="w-[560px]">
      <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
        <div className="col-span-2">
          <Input label="배송지 이름" placeholder="예: 집, 회사" value={addressName} maxLength={20} onChange={(e) => setAddressName(e.target.value)} required />
        </div>
        <AddressFields value={fields} onChange={setFields} />
        {mutation.isError && (
          <div className="col-span-2">
            <Notice tone="error">{errorMessage(mutation.error)}</Notice>
          </div>
        )}
        <div className="col-span-2 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose} disabled={mutation.isPending}>
            취소
          </Button>
          <Button type="submit" loading={mutation.isPending} disabled={!addressName.trim() || !isDeliveryFilled(fields)}>
            {address ? '저장' : '추가'}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
