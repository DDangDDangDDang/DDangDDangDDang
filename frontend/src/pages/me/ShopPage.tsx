import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Crown, Megaphone, ShoppingBag, UserPen, type LucideIcon } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, replaceSession, useSession, type Schema } from '@/api';
import { myInfoQueryKey } from '@/components/account/my-info-query';
import { DecoratedNickname } from '@/components/badge/DecoratedNickname';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, Dialog, EmptyState, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { DONATION_HISTORY_KEY, DONATION_WALLET_KEY } from '@/lib/donation';
import { formatDateTime, formatMileage } from '@/lib/format';
import { MY_BADGES_KEY, NICKNAME_BADGE_LABEL, type NicknameBadge } from '@/lib/nickname-badge';
import { sellerProfileKey } from '@/lib/use-seller';

type ShopItem = Schema<'ShopItemResponse'>;
type ShopItemType = NonNullable<ShopItem['item']>;

const SHOP_ITEMS_KEY = ['shop', 'items'] as const;

// 뱃지 상품은 아이콘 대신 꾸민 닉네임 미리 보기를 보여 준다.
const ITEM_ICON: Partial<Record<ShopItemType, LucideIcon>> = {
  NICKNAME_CHANGE: UserPen,
  AUCTION_BOOST: Megaphone,
  POWER_SELLER: Crown,
};

/**
 * 마일리지 상점: 도네이션 마일리지로 상품을 사면 바로 적용된다. (가격 고정, 되돌릴 수 없음)
 * - 닉네임 변경권: 변경 제한 기간(30일) 중에만 살 수 있고, 사면서 새 닉네임으로 바로 바꾼다.
 * - 닉네임 뱃지: 라이브 채팅의 닉네임을 꾸민다. 영구로 가지고, 사면 바로 장착된다. 내 뱃지에서 바꿔 끼거나 뺀다.
 * - 경매 위로 올리기: 내 시작 전·진행 중 경매를 골라 3시간 동안 경매 목록(최신순) 맨 위에 '홍보'로 올린다. (경매당 한 번, 동시 10개)
 * - 파워 판매자: 판매 확정 20건 이상인 인증 판매자가 영구로 한 번 산다. 판매자 프로필·방송 카드에 '파워' 표시
 */
export function ShopPage() {
  const queryClient = useQueryClient();
  const session = useSession();
  const nickname = session?.user.nickname ?? '내 닉네임';
  const wallet = useQuery({ queryKey: DONATION_WALLET_KEY, queryFn: () => call(api.GET('/api/v1/donation-points/wallet')) });
  const items = useQuery({ queryKey: SHOP_ITEMS_KEY, queryFn: () => call(api.GET('/api/v1/shop/items')) });
  const [buying, setBuying] = useState<ShopItem>();
  const [bought, setBought] = useState<string>();
  const balance = wallet.data?.balance ?? 0;

  const buyBadge = useMutation({
    mutationFn: (badge: NicknameBadge) => call(api.POST('/api/v1/shop/badges/{badge}', { params: { path: { badge } } })),
    onSuccess: (badges, badge) => {
      queryClient.setQueryData(MY_BADGES_KEY, badges);
      void queryClient.invalidateQueries({ queryKey: DONATION_WALLET_KEY });
      void queryClient.invalidateQueries({ queryKey: DONATION_HISTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: SHOP_ITEMS_KEY });
      setBuying(undefined);
      setBought(`${NICKNAME_BADGE_LABEL[badge]} 뱃지를 샀어요. 바로 장착했어요.`);
    },
  });

  const buyPowerSeller = useMutation({
    mutationFn: () => call(api.POST('/api/v1/shop/power-seller')),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: DONATION_WALLET_KEY });
      void queryClient.invalidateQueries({ queryKey: DONATION_HISTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: SHOP_ITEMS_KEY });
      // 판매자 프로필의 파워 판매자 표시
      const userId = session?.user.userId;
      if (userId !== undefined) void queryClient.invalidateQueries({ queryKey: sellerProfileKey(userId) });
      setBuying(undefined);
      setBought("파워 판매자가 되었어요. 판매자 프로필과 방송 카드에 '파워'가 붙어요.");
    },
  });

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
                <ShopItemRow key={item.item} item={item} nickname={nickname} balance={balance} onBuy={() => setBuying(item)} />
              ))}
            </ul>
          )}
        </CardBody>
      </Card>

      <MyBadgesCard nickname={nickname} />

      <NicknameChangeDialog
        open={buying?.item === 'NICKNAME_CHANGE'}
        item={buying}
        onClose={() => setBuying(undefined)}
        onBought={(changed) => {
          setBuying(undefined);
          setBought(`닉네임을 ‘${changed}’(으)로 바꿨어요.`);
        }}
      />

      <AuctionBoostDialog
        open={buying?.item === 'AUCTION_BOOST'}
        item={buying}
        onClose={() => setBuying(undefined)}
        onBoosted={(productName, endsAt) => {
          setBuying(undefined);
          setBought(`‘${productName}’ 경매를 ${formatDateTime(endsAt)}까지 경매 목록 맨 위에 올렸어요.`);
        }}
      />

      <ConfirmDialog
        open={buying?.item === 'POWER_SELLER'}
        title="파워 판매자가 될까요?"
        description={
          buyPowerSeller.isError ? (
            <span className="text-brand-700">{errorMessage(buyPowerSeller.error)}</span>
          ) : (
            `도네이션 마일리지 ${formatMileage(buying?.price ?? 0)}이 빠지고 되돌릴 수 없어요. 판매자 프로필과 방송 카드에 '파워' 표시가 영구로 붙어요.`
          )
        }
        confirmLabel={`${formatMileage(buying?.price ?? 0)}으로 사기`}
        loading={buyPowerSeller.isPending}
        onConfirm={() => buyPowerSeller.mutate()}
        onCancel={() => {
          setBuying(undefined);
          buyPowerSeller.reset();
        }}
      />

      <ConfirmDialog
        open={Boolean(buying?.badge)}
        title={`${buying?.name ?? ''}를 살까요?`}
        description={
          buyBadge.isError ? (
            <span className="text-brand-700">{errorMessage(buyBadge.error)}</span>
          ) : (
            `도네이션 마일리지 ${formatMileage(buying?.price ?? 0)}이 빠지고 되돌릴 수 없어요. 영구로 가지고, 사면 바로 장착돼요.`
          )
        }
        confirmLabel={`${formatMileage(buying?.price ?? 0)}으로 사기`}
        loading={buyBadge.isPending}
        onConfirm={() => buying?.badge && buyBadge.mutate(buying.badge)}
        onCancel={() => {
          setBuying(undefined);
          buyBadge.reset();
        }}
      />
    </div>
  );
}

function ShopItemRow({ item, nickname, balance, onBuy }: { item: ShopItem; nickname: string; balance: number; onBuy: () => void }) {
  const Icon = (item.item && ITEM_ICON[item.item]) || ShoppingBag;
  const short = (item.price ?? 0) > balance;
  const reason = !item.available ? item.unavailableReason : short ? '마일리지가 부족해요' : undefined;

  return (
    <li className="flex items-center justify-between gap-4 rounded-md border border-neutral-100 px-5 py-4">
      <div className="flex items-start gap-3">
        {item.badge ? (
          <span aria-hidden="true" className="flex h-10 min-w-24 shrink-0 items-center justify-center rounded-md bg-neutral-25 px-3 text-label font-semibold text-neutral-700">
            <DecoratedNickname nickname={nickname} badge={item.badge} />
          </span>
        ) : (
          <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-700">
            <Icon className="size-5" aria-hidden="true" />
          </span>
        )}
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

/** 경매 위로 올리기: 내 시작 전·진행 중 경매 중 하나를 골라 올린다. 실패하면(이미 올림, 자리 없음) 마일리지는 빠지지 않는다. */
function AuctionBoostDialog({
  open,
  item,
  onClose,
  onBoosted,
}: {
  open: boolean;
  item?: ShopItem;
  onClose: () => void;
  onBoosted: (productName: string, endsAt: string) => void;
}) {
  const queryClient = useQueryClient();
  const [auctionId, setAuctionId] = useState<number>();
  const auctions = useQuery({
    queryKey: ['auctions', 'mine', 'open'],
    queryFn: () => call(api.GET('/api/v1/users/me/auctions')),
    enabled: open,
  });
  const boost = useMutation({
    mutationFn: (id: number) => call(api.POST('/api/v1/shop/auction-boosts', { body: { auctionId: id } })),
    onSuccess: (data, id) => {
      void queryClient.invalidateQueries({ queryKey: DONATION_WALLET_KEY });
      void queryClient.invalidateQueries({ queryKey: DONATION_HISTORY_KEY });
      void queryClient.invalidateQueries({ queryKey: SHOP_ITEMS_KEY });
      void queryClient.invalidateQueries({ queryKey: ['auctions'] });
      const name = auctions.data?.find((auction) => auction.id === id)?.productName ?? '내';
      setAuctionId(undefined);
      onBoosted(name, data?.endsAt ?? '');
    },
  });
  const mine = auctions.data ?? [];

  function close() {
    setAuctionId(undefined);
    boost.reset();
    onClose();
  }

  return (
    <Dialog
      open={open}
      title="경매 위로 올리기"
      description={`${formatMileage(item?.price ?? 0)}이 빠지고 되돌릴 수 없어요. 3시간 동안 경매 목록(최신순) 맨 위에 '홍보'로 보이고, 경매 하나는 한 번만 올릴 수 있어요.`}
      onClose={close}
      closeDisabled={boost.isPending}
    >
      <div className="flex flex-col gap-4">
        {auctions.isPending ? (
          <Skeleton className="h-24" />
        ) : auctions.isError ? (
          <ErrorState message={errorMessage(auctions.error)} onRetry={() => void auctions.refetch()} />
        ) : mine.length === 0 ? (
          <EmptyState title="올릴 수 있는 경매가 없어요" description="시작 전이거나 진행 중인 내 경매만 올릴 수 있어요." />
        ) : (
          <fieldset className="flex max-h-72 flex-col gap-2 overflow-y-auto">
            <legend className="sr-only">올릴 경매</legend>
            {mine.map((auction) => (
              <label
                key={auction.id}
                className="flex cursor-pointer items-center gap-3 rounded-md border border-neutral-100 px-4 py-3 has-[:checked]:border-brand-500 has-[:checked]:bg-brand-50"
              >
                <input
                  type="radio"
                  name="boost-auction"
                  value={auction.id}
                  checked={auctionId === auction.id}
                  onChange={() => setAuctionId(auction.id)}
                  className="accent-brand-500"
                />
                <span className="flex flex-col">
                  <span className="text-body font-semibold">{auction.productName}</span>
                  <span className="text-caption text-neutral-500">
                    {auction.status === 'ONGOING' ? '진행 중' : '시작 전'} · {auction.endAt ? `${formatDateTime(auction.endAt)} 마감` : ''}
                  </span>
                </span>
              </label>
            ))}
          </fieldset>
        )}
        {boost.isError && <Notice tone="error">{errorMessage(boost.error)}</Notice>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={close} disabled={boost.isPending}>
            취소
          </Button>
          <Button loading={boost.isPending} disabled={auctionId === undefined} onClick={() => auctionId !== undefined && boost.mutate(auctionId)}>
            {formatMileage(item?.price ?? 0)}으로 올리기
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

/** 내 뱃지: 가진 뱃지 중 하나를 장착하거나 뺀다. 라이브 채팅에서 다음 메시지부터 바뀐다. */
function MyBadgesCard({ nickname }: { nickname: string }) {
  const queryClient = useQueryClient();
  const badges = useQuery({ queryKey: MY_BADGES_KEY, queryFn: () => call(api.GET('/api/v1/users/me/badges')) });
  const equip = useMutation({
    mutationFn: (badge: NicknameBadge | null) => call(api.PUT('/api/v1/users/me/badge', { body: { badge: badge ?? undefined } })),
    onSuccess: (data) => queryClient.setQueryData(MY_BADGES_KEY, data),
  });
  const owned = badges.data?.owned ?? [];
  const equipped = badges.data?.equipped;

  return (
    <Card>
      <CardHeader>
        <CardTitle>내 뱃지</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-3">
        {badges.isPending ? (
          <Skeleton className="h-16" />
        ) : badges.isError ? (
          <ErrorState message={errorMessage(badges.error)} onRetry={() => void badges.refetch()} />
        ) : owned.length === 0 ? (
          <EmptyState title="가진 뱃지가 없어요" description="닉네임 뱃지를 사면 라이브 채팅에서 내 닉네임이 꾸며져요." />
        ) : (
          <>
            <ul aria-label="내 뱃지" className="flex flex-wrap gap-2">
              {owned.map((badge) => {
                const isEquipped = badge === equipped;
                return (
                  <li key={badge}>
                    <button
                      type="button"
                      aria-pressed={isEquipped}
                      aria-label={`${NICKNAME_BADGE_LABEL[badge]} 장착`}
                      disabled={equip.isPending}
                      onClick={() => !isEquipped && equip.mutate(badge)}
                      className={
                        isEquipped
                          ? 'flex flex-col items-center gap-1 rounded-md border border-brand-500 bg-brand-50 px-4 py-2 text-label'
                          : 'flex flex-col items-center gap-1 rounded-md border border-neutral-200 px-4 py-2 text-label hover:bg-neutral-50'
                      }
                    >
                      <span className="font-semibold text-neutral-700">
                        <DecoratedNickname nickname={nickname} badge={badge} />
                      </span>
                      <span className="text-caption text-neutral-500">{isEquipped ? '장착 중' : NICKNAME_BADGE_LABEL[badge]}</span>
                    </button>
                  </li>
                );
              })}
            </ul>
            <div className="flex items-center gap-3">
              <Button variant="outline" size="sm" disabled={!equipped || equip.isPending} onClick={() => equip.mutate(null)}>
                뱃지 빼기
              </Button>
              {equip.isError && <span className="text-label text-brand-700">{errorMessage(equip.error)}</span>}
            </div>
          </>
        )}
      </CardBody>
    </Card>
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
