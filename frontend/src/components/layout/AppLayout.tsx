import { useQuery } from "@tanstack/react-query";
import {
  ChevronDown,
  CircleUser,
  Gift,
  Menu,
  MessageCircle,
  Search,
  UserCog,
  Wallet,
} from "lucide-react";
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  Link,
  matchPath,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router";
import { api, call, logout, useSession } from "@/api";
import { BroadcastProvider } from "@/components/broadcast/BroadcastProvider";
import { MiniBroadcast } from "@/components/broadcast/MiniBroadcast";
import { MyInfoDialog } from "@/components/account/MyInfoDialog";
import { DmRealtime } from "@/components/dm/DmRealtime";
import { BrandMark } from "@/components/layout/BrandMark";
import { SideNav } from "@/components/layout/SideNav";
import { LiveStartToasts } from "@/components/notification/LiveStartToasts";
import { NotificationBell } from "@/components/notification/NotificationBell";
import { useOpenSellerProfile } from "@/components/seller/seller-profile-context";
import { SellerProfileProvider } from "@/components/seller/SellerProfileProvider";
import { Button, buttonVariants } from "@/components/ui";
import { DONATION_WALLET_KEY } from "@/lib/donation";
import { formatNumber } from "@/lib/format";
import { MiniPlayer } from "@/components/watch/MiniPlayer";
import { WatchProvider } from "@/components/watch/WatchProvider";

/**
 * 모든 화면 공통 틀: 상단 헤더(검색) + 왼쪽 메뉴(SideNav) + 가운데 정렬된 본문 (최대 폭 1360px)
 * 판매자 송출(BroadcastProvider)을 여기에 두어 방송 중에 다른 화면으로 가도 송출이 이어지고, 작은 방송 창이 뜬다.
 * 판매자 프로필 창(SellerProfileProvider)과 방송 시작 알림 팝업(LiveStartToasts)도 앱 전체에 하나씩 둔다.
 * 시청자 방송 시청(WatchProvider)도 여기에 두어, 방송을 보다가 다른 화면으로 가면 오른쪽 아래 작은 플레이어(MiniPlayer)에서 이어진다.
 */
export function AppLayout() {
  const session = useSession();
  const { pathname } = useLocation();
  const showNav = !NO_NAV_PATHS.some((path) => matchPath(path, pathname));
  const collapsedByPage = COLLAPSED_NAV_PATHS.some((path) =>
    matchPath(path, pathname),
  );
  // 헤더의 메뉴 버튼으로 접고 편 상태는 그 화면에 있는 동안만 유지한다. 다른 화면으로 가면 화면 기본값으로 돌아간다.
  const [toggled, setToggled] = useState<{ path: string; collapsed: boolean }>();
  const collapsed =
    toggled?.path === pathname ? toggled.collapsed : collapsedByPage;

  return (
    <BroadcastProvider>
      <WatchProvider>
        <SellerProfileProvider>
          <div className="min-h-screen">
            {session?.user.userId !== undefined && (
              <DmRealtime userId={session.user.userId} />
            )}
            <AppHeader
              onToggleNav={
                showNav
                  ? () => setToggled({ path: pathname, collapsed: !collapsed })
                  : undefined
              }
              navCollapsed={collapsed}
            />
            <div className="flex">
              {showNav && <SideNav collapsed={collapsed} />}
              <main className="min-w-0 flex-1 px-8 pt-6 pb-14">
                <div className="mx-auto max-w-[1360px]">
                  <Outlet />
                </div>
              </main>
            </div>
            <MiniBroadcast />
            <MiniPlayer />
            {session && <LiveStartToasts />}
          </div>
        </SellerProfileProvider>
      </WatchProvider>
    </BroadcastProvider>
  );
}

/** 왼쪽 메뉴를 두지 않는 화면: 로그인·회원가입처럼 한 가지 일만 하는 화면 */
const NO_NAV_PATHS = [
  "/login",
  "/signup",
  "/signup/kakao",
  "/find-email",
  "/password-reset",
  "/oauth/*",
];

/** 왼쪽 메뉴를 접어 두는 화면: 영상·채팅처럼 본문이 넓어야 하는 화면 */
const COLLAPSED_NAV_PATHS = [
  "/lives/:liveStreamId",
  "/studio",
  "/messages",
  "/messages/:userId",
];

function AppHeader({
  onToggleNav,
  navCollapsed,
}: {
  onToggleNav?: () => void;
  navCollapsed: boolean;
}) {
  const session = useSession();
  const navigate = useNavigate();
  // 로그인·회원가입 뒤 지금 보던 화면으로 돌아온다. (로그인·회원가입 화면 자체에서는 그대로)
  const location = useLocation();
  const here = location.pathname + location.search;
  const back = ["/login", "/signup"].includes(location.pathname)
    ? ""
    : `?redirect=${encodeURIComponent(here)}`;
  const [myInfoOpen, setMyInfoOpen] = useState(false);
  const openProfile = useOpenSellerProfile();

  return (
    <header className="sticky top-0 z-30 border-b border-neutral-100 bg-neutral-25">
      <div className="flex h-16 items-center gap-4 px-4">
        <div className="flex w-[228px] shrink-0 items-center gap-2">
          {onToggleNav && (
            <button
              type="button"
              aria-label={navCollapsed ? "메뉴 펼치기" : "메뉴 접기"}
              aria-expanded={!navCollapsed}
              onClick={onToggleNav}
              className="flex size-11 items-center justify-center rounded-md text-neutral-700 hover:bg-neutral-50 hover:text-neutral-900"
            >
              <Menu className="size-5" aria-hidden="true" />
            </button>
          )}
          <Link to="/" className="flex items-center gap-2.5 px-1">
            <BrandMark />
            <span className="text-logo font-bold tracking-tight text-brand-500">땅땅땅</span>
          </Link>
        </div>

        <HeaderSearch />

        {session ? (
          <div className="flex shrink-0 items-center gap-2">
            <NotificationBell />
            <DmLink />
            <PointBalanceLink />
            <DonationPointLink />
            <AccountMenu
              nickname={session.user.nickname}
              onOpenMyInfo={() => setMyInfoOpen(true)}
              userId={session.user.userId}
              onOpenProfile={openProfile}
            />
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                // 서버 요청이 실패해도 logout()이 이 화면의 로그인 상태는 지운다. 어떤 경우든 홈으로 간다.
                void logout()
                  .catch(() => undefined)
                  .finally(() => navigate("/"));
              }}
            >
              로그아웃
            </Button>
          </div>
        ) : (
          <div className="flex shrink-0 items-center gap-2">
            <Link
              to={`/login${back}`}
              className={buttonVariants({ variant: "ghost", size: "sm" })}
            >
              로그인
            </Link>
            <Link
              to={`/signup${back}`}
              className={buttonVariants({ variant: "primary", size: "sm" })}
            >
              회원가입
            </Link>
          </div>
        )}
      </div>
      {session && (
        <MyInfoDialog open={myInfoOpen} onClose={() => setMyInfoOpen(false)} />
      )}
    </header>
  );
}

/**
 * 헤더의 닉네임 메뉴: 누르면 "내 정보"(정보 수정 창)와 "내 프로필"(다른 회원에게 보이는 프로필 창)이 펼쳐진다.
 * 바깥을 누르거나 Esc를 누르면 닫는다.
 */
function AccountMenu({
  nickname,
  userId,
  onOpenMyInfo,
  onOpenProfile,
}: {
  nickname?: string;
  userId?: number;
  onOpenMyInfo: () => void;
  onOpenProfile: (userId: number) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const choose = (action: () => void) => {
    setOpen(false);
    action();
  };

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`${nickname ?? "내"} 메뉴`}
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1 rounded-md px-2 py-2 text-body font-semibold hover:bg-neutral-50"
      >
        {nickname}
        <ChevronDown
          className={`size-4 text-neutral-500 transition-transform ${open ? "rotate-180" : ""}`}
          aria-hidden="true"
        />
      </button>
      {open && (
        <div
          role="menu"
          aria-label="내 메뉴"
          className="absolute top-12 right-0 z-50 flex w-40 flex-col rounded-lg border border-neutral-100 bg-neutral-0 py-1 shadow-md"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => choose(onOpenMyInfo)}
            className="flex items-center gap-2 px-3 py-2.5 text-left text-body hover:bg-neutral-50"
          >
            <UserCog className="size-4 text-neutral-500" aria-hidden="true" />
            내 정보
          </button>
          {userId !== undefined && (
            <button
              type="button"
              role="menuitem"
              onClick={() => choose(() => onOpenProfile(userId))}
              className="flex items-center gap-2 px-3 py-2.5 text-left text-body hover:bg-neutral-50"
            >
              <CircleUser className="size-4 text-neutral-500" aria-hidden="true" />
              내 프로필
            </button>
          )}
        </div>
      )}
    </div>
  );
}

/** 헤더 가운데 검색: 경매 상품명으로 찾아 경매 목록 화면으로 간다. */
function HeaderSearch() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const q = keyword.trim();
    navigate(q ? `/auctions?q=${encodeURIComponent(q)}` : "/auctions");
  };

  return (
    <form
      role="search"
      onSubmit={submit}
      className="flex min-w-0 flex-1 justify-center"
    >
      <label htmlFor="header-search" className="sr-only">
        경매 상품 검색
      </label>
      <div className="flex h-11 w-full max-w-[480px] items-center gap-2 rounded-full border border-neutral-100 bg-neutral-0 pr-1.5 pl-4 focus-within:border-neutral-400">
        <input
          id="header-search"
          type="search"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="경매 상품 검색"
          className="min-w-0 flex-1 bg-transparent text-body text-neutral-900 outline-none placeholder:text-neutral-500"
        />
        <button
          type="submit"
          aria-label="검색"
          className="flex size-8 items-center justify-center rounded-full bg-neutral-50 text-neutral-700 hover:text-neutral-900"
        >
          <Search className="size-4" aria-hidden="true" />
        </button>
      </div>
    </form>
  );
}

/** 헤더의 1:1 채팅 바로가기 + 읽지 않은 메시지 수 */
function DmLink() {
  const unread = useQuery({
    queryKey: ["dm", "unread"],
    queryFn: () => call(api.GET("/api/v1/dm/unread-count")),
  });
  const count = unread.data?.unreadCount ?? 0;

  return (
    <Link
      to="/messages"
      aria-label={count > 0 ? `메시지, 읽지 않은 메시지 ${count}개` : "메시지"}
      className="relative flex size-10 items-center justify-center rounded-full border border-neutral-100 text-neutral-700 hover:bg-neutral-50"
    >
      <MessageCircle className="size-[18px]" aria-hidden="true" />
      {count > 0 && (
        <span
          aria-hidden="true"
          className="num absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-caption font-bold text-white"
        >
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}

/** 헤더의 도네이션 마일리지 (쓸 수 있는 마일리지). 누르면 도네이션 마일리지 화면으로 간다. 현금 포인트(P)와 헷갈리지 않게 단위는 M */
function DonationPointLink() {
  const wallet = useQuery({
    queryKey: DONATION_WALLET_KEY,
    queryFn: () => call(api.GET("/api/v1/donation-points/wallet")),
  });
  const balance = wallet.data?.balance ?? 0;

  return (
    <Link
      to="/me/donation-points"
      className="flex h-10 items-center gap-2 rounded-full border border-neutral-100 px-3.5 font-semibold hover:bg-neutral-50"
      aria-label={
        wallet.data
          ? `도네이션 마일리지 ${formatNumber(balance)} M`
          : "도네이션 마일리지"
      }
      title="도네이션 마일리지 (판매자에게 선물할 수 있어요)"
    >
      <Gift className="size-[18px] text-brand-500" aria-hidden="true" />
      <span className="num">{wallet.data ? formatNumber(balance) : "-"}</span>
      <span className="text-neutral-500">M</span>
    </Link>
  );
}

/** 헤더의 사용 가능 포인트. 누르면 포인트 화면으로 간다. */
function PointBalanceLink() {
  const wallet = useQuery({
    queryKey: ["points", "wallet"],
    queryFn: () => call(api.GET("/api/v1/points/wallet")),
  });

  return (
    <Link
      to="/points"
      className="flex h-10 items-center gap-2 rounded-full border border-neutral-100 px-3.5 font-semibold hover:bg-neutral-50"
      aria-label={
        wallet.data
          ? `사용 가능 포인트 ${formatNumber(wallet.data.balance ?? 0)} P`
          : "포인트"
      }
    >
      <Wallet className="size-[18px] text-neutral-500" aria-hidden="true" />
      <span className="num">
        {wallet.data ? formatNumber(wallet.data.balance ?? 0) : "-"}
      </span>
      <span className="text-neutral-500">P</span>
    </Link>
  );
}
