import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Crown } from 'lucide-react';
import { useState } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, EmptyState, ErrorState, Notice, Skeleton } from '@/components/ui';
import { DONATION_HISTORY_KEY, DONATION_WALLET_KEY } from '@/lib/donation';
import { formatDateTime, formatNumber, formatMileage } from '@/lib/format';

type ShopItem = Schema<'SellerShopItemResponse'>;

const SHOP_KEY = ['donation', 'shop'] as const;
const MY_PERKS_KEY = ['donation', 'perks', 'mine'] as const;

const PERK_LABEL: Record<NonNullable<ShopItem['type']>, string> = {
  POWER_SELLER: '파워 판매자',
};

/**
 * 판매자 상점: 시청자에게 선물받은 도네이션 마일리지로 혜택을 산다. (인증 판매자만)
 * 파워 판매자: 기간 동안 라이브 목록 맨 위에 방송이 올라가고, 프로필·방송 카드에 파워 판매자 배지가 붙는다.
 * 진행 중일 때 또 사면 끝나는 날부터 이어 붙는다.
 */
export function SellerShopPage() {
  const queryClient = useQueryClient();
  const wallet = useQuery({ queryKey: DONATION_WALLET_KEY, queryFn: () => call(api.GET('/api/v1/donation-points/wallet')) });
  const shop = useQuery({ queryKey: SHOP_KEY, queryFn: () => call(api.GET('/api/v1/seller-perks/shop')) });
  const mine = useQuery({ queryKey: MY_PERKS_KEY, queryFn: () => call(api.GET('/api/v1/seller-perks/me')) });
  const [buying, setBuying] = useState<ShopItem>();
  const [bought, setBought] = useState<string>();

  const purchase = useMutation({
    mutationFn: (item: ShopItem) => call(api.POST('/api/v1/seller-perks', { body: { type: item.type ?? 'POWER_SELLER' } })),
    onSuccess: (perk, item) => {
      setBuying(undefined);
      setBought(`${item.name}를 샀어요.${perk?.endsAt ? ` ${formatDateTime(perk.endsAt)}까지 이어져요.` : ''}`);
      void queryClient.invalidateQueries({ queryKey: DONATION_WALLET_KEY });
      void queryClient.invalidateQueries({ queryKey: DONATION_HISTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: MY_PERKS_KEY });
      // 판매자 프로필의 파워 판매자 배지
      void queryClient.invalidateQueries({ queryKey: ['seller-profile'] });
    },
  });

  const balance = wallet.data?.receivedBalance ?? 0;

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="판매자 상점" description="시청자에게 선물받은 도네이션 마일리지로 방송을 알리는 혜택을 살 수 있어요." />

      <Card>
        <div className="flex flex-col gap-1 px-6 py-5">
          <span className="text-label text-neutral-500">선물받은 마일리지</span>
          <strong className="num text-price-lg font-bold">{wallet.isPending ? <Skeleton className="h-8 w-32" /> : formatMileage(balance)}</strong>
        </div>
      </Card>

      {bought && <Notice tone="success">{bought}</Notice>}

      <Card>
        <CardHeader>
          <CardTitle>상품</CardTitle>
        </CardHeader>
        <CardBody>
          {shop.isPending ? (
            <Skeleton className="h-28" />
          ) : shop.isError ? (
            <ErrorState message={errorMessage(shop.error)} onRetry={() => void shop.refetch()} />
          ) : (
            <ul className="flex flex-col gap-3">
              {(shop.data ?? []).map((item) => {
                const short = (item.price ?? 0) > balance;
                return (
                  <li key={item.type} className="flex items-center justify-between gap-4 rounded-md border border-neutral-100 px-5 py-4">
                    <div className="flex items-start gap-3">
                      <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
                        <Crown className="size-5" aria-hidden="true" />
                      </span>
                      <div className="flex flex-col gap-1">
                        <strong className="text-body-lg font-bold">{item.name}</strong>
                        <span className="text-body text-neutral-700">{item.description}</span>
                        <span className="text-label text-neutral-500">
                          <span className="num font-semibold text-neutral-900">{formatMileage(item.price ?? 0)}</span> · {formatNumber(item.periodDays ?? 0)}일
                        </span>
                      </div>
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      <Button disabled={short} onClick={() => setBuying(item)}>
                        사기
                      </Button>
                      {short && <span className="text-caption text-neutral-500">선물받은 마일리지가 부족해요</span>}
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </CardBody>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>내 혜택</CardTitle>
        </CardHeader>
        <CardBody>
          {mine.isPending ? (
            <Skeleton className="h-16" />
          ) : mine.isError ? (
            <ErrorState message={errorMessage(mine.error)} onRetry={() => void mine.refetch()} />
          ) : (mine.data ?? []).length === 0 ? (
            <EmptyState title="진행 중인 혜택이 없어요" description="상품을 사면 여기에서 기간을 확인할 수 있어요." />
          ) : (
            <ul aria-label="내 혜택" className="flex flex-col gap-2">
              {(mine.data ?? []).map((perk) => {
                const started = perk.startsAt ? new Date(perk.startsAt).getTime() <= Date.now() : true;
                return (
                  <li key={perk.id} className="flex items-center justify-between gap-4 rounded-md bg-neutral-25 px-4 py-3 text-body">
                    <span className="font-semibold">
                      {perk.type ? PERK_LABEL[perk.type] : '혜택'}{' '}
                      <span className="text-label font-normal text-neutral-500">{started ? '진행 중' : '예정'}</span>
                    </span>
                    <span className="text-neutral-700">
                      {perk.startsAt ? formatDateTime(perk.startsAt) : '-'} ~ {perk.endsAt ? formatDateTime(perk.endsAt) : '-'}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </CardBody>
      </Card>

      <ConfirmDialog
        open={buying !== undefined}
        title={`${buying?.name ?? ''}를 살까요?`}
        description={
          purchase.isError ? (
            <span className="text-brand-700">{errorMessage(purchase.error)}</span>
          ) : (
            `선물받은 마일리지 ${formatMileage(buying?.price ?? 0)}이 빠지고 되돌릴 수 없어요. 이미 진행 중이면 끝나는 날부터 ${formatNumber(buying?.periodDays ?? 0)}일 이어져요.`
          )
        }
        confirmLabel={`${formatMileage(buying?.price ?? 0)}으로 사기`}
        loading={purchase.isPending}
        onConfirm={() => buying && purchase.mutate(buying)}
        onCancel={() => {
          setBuying(undefined);
          purchase.reset();
        }}
      />
    </div>
  );
}
