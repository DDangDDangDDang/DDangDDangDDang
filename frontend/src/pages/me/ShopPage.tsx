import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ShoppingBag, UserPen, type LucideIcon } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, replaceSession, type Schema } from '@/api';
import { myInfoQueryKey } from '@/components/account/my-info-query';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, Card, CardBody, CardHeader, CardTitle, Dialog, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { DONATION_HISTORY_KEY, DONATION_WALLET_KEY } from '@/lib/donation';
import { formatMileage } from '@/lib/format';

type ShopItem = Schema<'ShopItemResponse'>;
type ShopItemType = NonNullable<ShopItem['item']>;

const SHOP_ITEMS_KEY = ['shop', 'items'] as const;

const ITEM_ICON: Record<ShopItemType, LucideIcon> = {
  NICKNAME_CHANGE: UserPen,
};

/**
 * 마일리지 상점: 도네이션 마일리지로 상품을 사면 바로 적용된다. (가격 고정, 되돌릴 수 없음)
 * - 닉네임 변경권: 변경 제한 기간(30일) 중에만 살 수 있고, 사면서 새 닉네임으로 바로 바꾼다.
 */
export function ShopPage() {
  const wallet = useQuery({ queryKey: DONATION_WALLET_KEY, queryFn: () => call(api.GET('/api/v1/donation-points/wallet')) });
  const items = useQuery({ queryKey: SHOP_ITEMS_KEY, queryFn: () => call(api.GET('/api/v1/shop/items')) });
  const [buying, setBuying] = useState<ShopItemType>();
  const [bought, setBought] = useState<string>();
  const balance = wallet.data?.balance ?? 0;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="마일리지 상점" description="도네이션 마일리지로 상품을 사면 바로 적용돼요. 산 상품은 되돌릴 수 없어요." />

      <Card>
        <div className="flex items-center justify-between gap-4 px-6 py-5">
          <div className="flex flex-col gap-1">
            <span className="text-label text-neutral-500">내 마일리지</span>
            <strong className="num text-price-lg font-bold">{wallet.isPending ? <Skeleton className="h-8 w-32" /> : formatMileage(balance)}</strong>
          </div>
          <Link to="/me/donation-points" className="text-label font-semibold text-neutral-500 underline hover:text-neutral-900">
            마일리지 내역 보기
          </Link>
        </div>
      </Card>

      {bought && <Notice tone="success">{bought}</Notice>}

      <Card>
        <CardHeader>
          <CardTitle>상품</CardTitle>
        </CardHeader>
        <CardBody>
          {items.isPending ? (
            <Skeleton className="h-28" />
          ) : items.isError ? (
            <ErrorState message={errorMessage(items.error)} onRetry={() => void items.refetch()} />
          ) : (
            <ul aria-label="상품" className="flex flex-col gap-3">
              {(items.data ?? []).map((item) => (
                <ShopItemRow key={item.item} item={item} balance={balance} onBuy={() => item.item && setBuying(item.item)} />
              ))}
            </ul>
          )}
        </CardBody>
      </Card>

      <NicknameChangeDialog
        open={buying === 'NICKNAME_CHANGE'}
        item={items.data?.find((item) => item.item === 'NICKNAME_CHANGE')}
        onClose={() => setBuying(undefined)}
        onBought={(nickname) => {
          setBuying(undefined);
          setBought(`닉네임을 ‘${nickname}’(으)로 바꿨어요.`);
        }}
      />
    </div>
  );
}

function ShopItemRow({ item, balance, onBuy }: { item: ShopItem; balance: number; onBuy: () => void }) {
  const Icon = (item.item && ITEM_ICON[item.item]) || ShoppingBag;
  const short = (item.price ?? 0) > balance;
  const reason = !item.available ? item.unavailableReason : short ? '마일리지가 부족해요' : undefined;

  return (
    <li className="flex items-center justify-between gap-4 rounded-md border border-neutral-100 px-5 py-4">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
          <Icon className="size-5" aria-hidden="true" />
        </span>
        <div className="flex flex-col gap-1">
          <strong className="text-body-lg font-bold">{item.name}</strong>
          <span className="text-body text-neutral-700">{item.description}</span>
          <span className="num text-label font-semibold text-neutral-900">{formatMileage(item.price ?? 0)}</span>
        </div>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <Button disabled={Boolean(reason)} onClick={onBuy} aria-label={`${item.name ?? '상품'} 사기`}>
          사기
        </Button>
        {reason && <span className="text-caption text-neutral-500">{reason}</span>}
      </div>
    </li>
  );
}

/** 닉네임 변경권: 새 닉네임을 받아 사면서 바로 바꾼다. 실패하면(중복 등) 마일리지는 빠지지 않는다. */
function NicknameChangeDialog({
  open,
  item,
  onClose,
  onBought,
}: {
  open: boolean;
  item?: ShopItem;
  onClose: () => void;
  onBought: (nickname: string) => void;
}) {
  const queryClient = useQueryClient();
  const [nickname, setNickname] = useState('');

  const purchase = useMutation({
    mutationFn: (next: string) => call(api.POST('/api/v1/shop/nickname-change', { body: { nickname: next } })),
    onSuccess: (data, next) => {
      // 토큰에 닉네임이 들어 있어서, 서버가 바뀐 닉네임이 담긴 새 토큰을 준다.
      if (data?.accessToken && data.user) replaceSession(data.accessToken, data.user);
      queryClient.setQueryData(myInfoQueryKey, data?.user);
      void queryClient.invalidateQueries({ queryKey: DONATION_WALLET_KEY });
      void queryClient.invalidateQueries({ queryKey: DONATION_HISTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: SHOP_ITEMS_KEY });
      setNickname('');
      onBought(next);
    },
  });

  function close() {
    setNickname('');
    purchase.reset();
    onClose();
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    purchase.mutate(nickname.trim());
  }

  return (
    <Dialog
      open={open}
      title="닉네임 변경권"
      description={`${formatMileage(item?.price ?? 0)}이 빠지고 되돌릴 수 없어요. 바꾼 날부터 다시 30일 동안 바꿀 수 없어요.`}
      onClose={close}
      closeDisabled={purchase.isPending}
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <Input
          label="새 닉네임"
          autoFocus
          value={nickname}
          maxLength={10}
          hint="2~10자로 입력해 주세요."
          error={purchase.isError ? errorMessage(purchase.error) : undefined}
          onChange={(e) => setNickname(e.target.value)}
        />
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={close} disabled={purchase.isPending}>
            취소
          </Button>
          <Button type="submit" loading={purchase.isPending} disabled={nickname.trim().length < 2}>
            {formatMileage(item?.price ?? 0)}으로 바꾸기
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
