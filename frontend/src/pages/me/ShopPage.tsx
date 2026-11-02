import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ChevronDown, Crown, Megaphone, ShoppingBag, UserPen, type LucideIcon } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { api, call, errorMessage, replaceSession, useSession, type Schema } from '@/api';
import { myInfoQueryKey } from '@/components/account/my-info-query';
import { DecoratedNickname, NicknameBadgePreview } from '@/components/badge/DecoratedNickname';
import { PageHeader } from '@/components/layout/PageHeader';
import { Button, Card, CardBody, CardHeader, CardTitle, ConfirmDialog, Dialog, EmptyState, ErrorState, Input, Notice, Skeleton } from '@/components/ui';
import { DONATION_HISTORY_KEY, DONATION_WALLET_KEY } from '@/lib/donation';
import { cn } from '@/lib/cn';
import { formatDateTime, formatMileage } from '@/lib/format';
import { MY_BADGES_KEY, NICKNAME_BADGE_KINDS, NICKNAME_BADGE_LABEL, nicknameBadgeKindOf, type NicknameBadge, type NicknameBadgeKind } from '@/lib/nickname-badge';
import { sellerProfileKey } from '@/lib/use-seller';

type ShopItem = Schema<'ShopItemResponse'>;
type ShopItemType = NonNullable<ShopItem['item']>;
type ShopAudience = NonNullable<ShopItem['audience']>;

const SHOP_ITEMS_KEY = ['shop', 'items'] as const;

const SHOP_TABS: { key: ShopAudience; label: string }[] = [
  { key: 'BUYER', label: '구매자' },
  { key: 'SELLER', label: '판매자' },
];

const ITEM_ICON: Partial<Record<ShopItemType, LucideIcon>> = {
  NICKNAME_CHANGE: UserPen,
  AUCTION_BOOST: Megaphone,
  POWER_SELLER: Crown,
};

/**
 * 마일리지 상점: 도네이션 마일리지로 상품을 사면 바로 적용된다. (가격 고정, 되돌릴 수 없음) 구매자·판매자 탭으로 나눈다.
 * - 닉네임 변경권: 변경 제한 기간(30일) 중에만 살 수 있고, 사면서 새 닉네임으로 바로 바꾼다.
 * - 채팅 스킨·채팅 뱃지·닉네임 색상: 라이브 채팅의 닉네임을 꾸민다. 종류를 펼쳐 하나를 골라 산다.
 *   영구로 가지고, 사면 바로 장착된다. 종류마다 하나씩 함께 쓸 수 있고, 내 꾸미기에서 바꾸거나 뺀다.
 * - 경매 위로 올리기: 내 시작 전·진행 중 경매를 골라 3시간 동안 경매 목록(최신순) 맨 위에 '홍보'로 올린다. (경매당 한 번, 동시 10개)
 * - 파워 판매자: 판매 확정 20건 이상인 인증 판매자가 영구로 한 번 산다. 판매자 프로필·방송 카드에 '파워' 표시
 */
export function ShopPage() {
  const queryClient = useQueryClient();
  const session = useSession();
  const nickname = session?.user.nickname ?? '내 닉네임';
  const wallet = useQuery({ queryKey: DONATION_WALLET_KEY, queryFn: () => call(api.GET('/api/v1/donation-points/wallet')) });
  const items = useQuery({ queryKey: SHOP_ITEMS_KEY, queryFn: () => call(api.GET('/api/v1/shop/items')) });
  const [tab, setTab] = useState<ShopAudience>('BUYER');
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
      setBought(`‘${NICKNAME_BADGE_LABEL[badge]}’을(를) 샀어요. 바로 장착했어요.`);
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

      <div role="tablist" aria-label="상품 종류" className="flex gap-1 border-b border-neutral-100">
        {SHOP_TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            aria-selected={tab === item.key}
            onClick={() => setTab(item.key)}
            className={cn(
              '-mb-px border-b-2 px-4 py-2.5 text-body font-semibold transition-colors',
              tab === item.key ? 'border-neutral-900 text-neutral-900' : 'border-transparent text-neutral-500 hover:text-neutral-900',
            )}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div role="tabpanel" aria-label={SHOP_TABS.find((item) => item.key === tab)?.label} className="flex flex-col gap-4">
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
              <ShopItemList
                items={(items.data ?? []).filter((item) => item.audience === tab)}
                nickname={nickname}
                balance={balance}
                onBuy={setBuying}
              />
            )}
          </CardBody>
        </Card>

        {tab === 'BUYER' && <MyBadgesCard nickname={nickname} />}
      </div>

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
        title={`‘${buying?.name ?? ''}’을(를) 살까요?`}
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

function ShopItemList({ items, nickname, balance, onBuy }: { items: ShopItem[]; nickname: string; balance: number; onBuy: (item: ShopItem) => void }) {
  const plain = items.filter((item) => !item.badgeKind);
  const groups = NICKNAME_BADGE_KINDS.map(({ kind, label }) => ({ kind, label, items: items.filter((item) => item.badgeKind === kind) })).filter(
    (group) => group.items.length > 0,
  );

  return (
    <ul aria-label="상품" className="flex flex-col gap-3">
      {plain.map((item) => (
        <ShopItemRow key={item.item} item={item} balance={balance} onBuy={() => onBuy(item)} />
      ))}
      {groups.map((group) => (
        <BadgeGroup key={group.kind} label={group.label} items={group.items} nickname={nickname} balance={balance} onBuy={onBuy} />
      ))}
    </ul>
  );
}

function ShopItemRow({ item, balance, onBuy }: { item: ShopItem; balance: number; onBuy: () => void }) {
  const Icon = (item.item && ITEM_ICON[item.item]) || ShoppingBag;
  const reason = unavailableReason(item, balance);

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

/** 꾸미기 종류 하나: 누르면 펼쳐지고, 내 닉네임에 적용한 미리 보기를 보며 하나를 골라 산다. */
function BadgeGroup({ label, items, nickname, balance, onBuy }: { label: string; items: ShopItem[]; nickname: string; balance: number; onBuy: (item: ShopItem) => void }) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<ShopItemType>();
  const chosen = items.find((item) => item.item === selected);
  const reason = chosen ? unavailableReason(chosen, balance) : undefined;
  const prices = items.map((item) => item.price ?? 0);

  return (
    <li className="rounded-md border border-neutral-100">
      <button type="button" aria-expanded={open} onClick={() => setOpen(!open)} className="flex w-full items-center justify-between gap-4 px-5 py-4 text-left">
        <span className="flex flex-col gap-1">
          <strong className="text-body-lg font-bold">{label}</strong>
          <span className="text-body text-neutral-700">
            {items.length}종 · <span className="num">{formatMileage(Math.min(...prices))}</span>
          </span>
        </span>
        <ChevronDown className={cn('size-5 shrink-0 text-neutral-500 transition-transform', open && 'rotate-180')} aria-hidden="true" />
      </button>
      {open && (
        <div className="flex flex-col gap-3 border-t border-neutral-100 px-5 py-4">
          <fieldset className="grid grid-cols-2 gap-2">
            <legend className="sr-only">{label} 고르기</legend>
            {items.map((item) => (
              <label
                key={item.item}
                className="flex cursor-pointer items-center gap-3 rounded-md border border-neutral-100 px-4 py-3 has-[:checked]:border-brand-500 has-[:checked]:bg-brand-50"
              >
                <input
                  type="radio"
                  name={`shop-${label}`}
                  checked={selected === item.item}
                  onChange={() => setSelected(item.item)}
                  aria-label={item.name}
                  className="accent-brand-500"
                />
                <span className="flex min-w-0 flex-col gap-1">
                  <span className="text-label font-semibold text-neutral-700">{item.badge && <NicknameBadgePreview nickname={nickname} badge={item.badge} />}</span>
                  <span className="text-caption text-neutral-500">{item.name}</span>
                </span>
              </label>
            ))}
          </fieldset>
          {chosen && <p className="text-body text-neutral-700">{chosen.description}</p>}
          <div className="flex items-center justify-end gap-3">
            {reason && <span className="text-caption text-neutral-500">{reason}</span>}
            <Button disabled={!chosen || Boolean(reason)} onClick={() => chosen && onBuy(chosen)} aria-label={`${label} 사기`}>
              {chosen ? `${formatMileage(chosen.price ?? 0)}으로 사기` : '골라 주세요'}
            </Button>
          </div>
        </div>
      )}
    </li>
  );
}

function unavailableReason(item: ShopItem, balance: number) {
  if (!item.available) return item.unavailableReason;
  return (item.price ?? 0) > balance ? '마일리지가 부족해요' : undefined;
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

/** 내 꾸미기: 종류마다 가진 것 중 하나를 장착하거나 뺀다. 라이브 채팅에서 다음 메시지부터 바뀐다. */
function MyBadgesCard({ nickname }: { nickname: string }) {
  const queryClient = useQueryClient();
  const badges = useQuery({ queryKey: MY_BADGES_KEY, queryFn: () => call(api.GET('/api/v1/users/me/badges')) });
  const equip = useMutation({
    mutationFn: (body: { badge?: NicknameBadge; kind?: NicknameBadgeKind }) => call(api.PUT('/api/v1/users/me/badge', { body })),
    onSuccess: (data) => queryClient.setQueryData(MY_BADGES_KEY, data),
  });
  const owned = badges.data?.owned ?? [];
  const equipped: Record<NicknameBadgeKind, NicknameBadge | undefined> = {
    SKIN: badges.data?.equipped,
    TITLE: badges.data?.equippedTitle,
    COLOR: badges.data?.equippedColor,
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>내 꾸미기</CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-4">
        {badges.isPending ? (
          <Skeleton className="h-16" />
        ) : badges.isError ? (
          <ErrorState message={errorMessage(badges.error)} onRetry={() => void badges.refetch()} />
        ) : owned.length === 0 ? (
          <EmptyState title="가진 꾸미기가 없어요" description="채팅 스킨, 채팅 뱃지, 닉네임 색상을 사면 라이브 채팅에서 내 닉네임이 꾸며져요." />
        ) : (
          <>
            <div className="flex items-center gap-2 rounded-md bg-neutral-25 px-4 py-3 text-label font-semibold text-neutral-700">
              <span className="text-caption font-normal text-neutral-500">채팅에서 이렇게 보여요</span>
              <DecoratedNickname nickname={nickname} badge={equipped.SKIN} title={equipped.TITLE} color={equipped.COLOR} />
            </div>
            {NICKNAME_BADGE_KINDS.map(({ kind, label }) => {
              const mine = owned.filter((badge) => nicknameBadgeKindOf(badge) === kind);
              if (mine.length === 0) return null;
              return (
                <section key={kind} aria-label={label} className="flex flex-col gap-2">
                  <div className="flex items-center justify-between gap-3">
                    <h3 className="text-label font-semibold text-neutral-700">{label}</h3>
                    <Button variant="outline" size="sm" disabled={!equipped[kind] || equip.isPending} onClick={() => equip.mutate({ kind })}>
                      {label} 빼기
                    </Button>
                  </div>
                  <ul aria-label={`내 ${label}`} className="flex flex-wrap gap-2">
                    {mine.map((badge) => {
                      const isEquipped = badge === equipped[kind];
                      return (
                        <li key={badge}>
                          <button
                            type="button"
                            aria-pressed={isEquipped}
                            aria-label={`${NICKNAME_BADGE_LABEL[badge]} 장착`}
                            disabled={equip.isPending}
                            onClick={() => !isEquipped && equip.mutate({ badge })}
                            className={
                              isEquipped
                                ? 'flex flex-col items-center gap-1 rounded-md border border-brand-500 bg-brand-50 px-4 py-2 text-label'
                                : 'flex flex-col items-center gap-1 rounded-md border border-neutral-200 px-4 py-2 text-label hover:bg-neutral-50'
                            }
                          >
                            <span className="font-semibold text-neutral-700">
                              <NicknameBadgePreview nickname={nickname} badge={badge} />
                            </span>
                            <span className="text-caption text-neutral-500">{isEquipped ? '장착 중' : NICKNAME_BADGE_LABEL[badge]}</span>
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                </section>
              );
            })}
            {equip.isError && <span className="text-label text-brand-700">{errorMessage(equip.error)}</span>}
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
