import { useQuery } from "@tanstack/react-query";
import { Gavel, Gift, MessageCircle, Wallet } from "lucide-react";
import { useState } from "react";
import {
  Link,
  matchPath,
  NavLink,
  Outlet,
  useLocation,
  useNavigate,
} from "react-router";
import { api, call, logout, useSession } from "@/api";
import { BroadcastProvider } from "@/components/broadcast/BroadcastProvider";
import { MiniBroadcast } from "@/components/broadcast/MiniBroadcast";
import { MyInfoDialog } from "@/components/account/MyInfoDialog";
import { DmRealtime } from "@/components/dm/DmRealtime";
import { LiveStartToasts } from "@/components/notification/LiveStartToasts";
import { NotificationBell } from "@/components/notification/NotificationBell";
import { SellerProfileProvider } from "@/components/seller/SellerProfileProvider";
import { SubscriptionSidebar } from "@/components/seller/SubscriptionSidebar";
import { Button, buttonVariants } from "@/components/ui";
import { cn } from "@/lib/cn";
import { DONATION_WALLET_KEY } from "@/lib/donation";
import { formatNumber } from "@/lib/format";
import { useSellerProfile } from "@/lib/use-seller";
import { WatchNavigationGuard } from "@/lib/watch-navigation";

/**
 * 모든 화면 공통 틀: 상단 헤더 + 가운데 정렬된 본문 (최대 폭 1360px)
 * 판매자 송출(BroadcastProvider)을 여기에 두어 방송 중에 다른 화면으로 가도 송출이 이어지고, 작은 방송 창이 뜬다.
 * 판매자 프로필 창(SellerProfileProvider)과 방송 시작 알림 팝업(LiveStartToasts)도 앱 전체에 하나씩 둔다.
 * 방송 시청 중에 다른 화면으로 가는 링크는 새 탭으로 연다. (WatchNavigationGuard, 시청이 끊기지 않게)
 */
export function AppLayout() {
  const session = useSession();
  const { pathname } = useLocation();
  const showSubscriptions = SUBSCRIPTION_SIDEBAR_PATHS.some((path) =>
    matchPath(path, pathname),
  );
  return (
    <BroadcastProvider>
      <SellerProfileProvider>
        <div className="min-h-screen">
          {session?.user.userId !== undefined && (
            <DmRealtime userId={session.user.userId} />
          )}
          <WatchNavigationGuard />
          <AppHeader />
          <main className="mx-auto max-w-[1360px] px-8 pt-6 pb-14">
            {/* 상단 탭 화면에서는 왼쪽에 구독 중인 판매자를 보여 준다. (방송 중이면 LIVE를 눌러 바로 들어간다) */}
            {showSubscriptions ? (
              <div className="flex items-start gap-8">
                <SubscriptionSidebar />
                <div className="min-w-0 flex-1">
                  <Outlet />
                </div>
              </div>
            ) : (
              <Outlet />
            )}
          </main>
          <MiniBroadcast />
          {session && <LiveStartToasts />}
        </div>
      </SellerProfileProvider>
    </BroadcastProvider>
  );
}

/**
 * 왼쪽 "구독 중인 판매자"를 보여 줄 화면: 상단 탭 화면(홈·라이브·경매·내 상품·주문 내역·관리자 화면).
 * 방송 시청·스튜디오처럼 오른쪽에 넓은 패널이 있는 화면과 로그인·회원가입 화면에는 두지 않는다.
 */
const SUBSCRIPTION_SIDEBAR_PATHS = [
  "/",
  "/lives",
  "/auctions",
  "/me/products",
  "/orders",
  "/admin/*",
];

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn(
    "rounded-md px-3 py-2 text-body font-semibold transition-colors",
    isActive ? "text-neutral-900" : "text-neutral-500 hover:text-neutral-900",
  );

function AppHeader() {
  const session = useSession();
  const navigate = useNavigate();
  // 로그인·회원가입 뒤 지금 보던 화면으로 돌아온다. (로그인·회원가입 화면 자체에서는 그대로)
  const location = useLocation();
  const here = location.pathname + location.search;
  const back = ["/login", "/signup"].includes(location.pathname)
    ? ""
    : `?redirect=${encodeURIComponent(here)}`;
  const [myInfoOpen, setMyInfoOpen] = useState(false);
  const verifiedSeller =
    useSellerProfile(session?.user.userId).data?.verifiedSeller ?? false;

  return (
    <header className="border-b border-neutral-100 bg-neutral-0">
      <div className="mx-auto flex h-16 max-w-[1360px] items-center justify-between gap-4 px-8">
        <div className="flex items-center gap-6">
          <Link to="/" className="flex items-center gap-2.5">
            <span className="flex size-8 items-center justify-center rounded-md bg-brand-500 text-neutral-0">
              <Gavel className="size-[18px]" aria-hidden="true" />
            </span>
            <span className="text-logo font-bold tracking-tight">땅땅땅</span>
          </Link>
          <nav aria-label="내 메뉴" className="flex items-center">
            <NavLink to="/" end className={navClass}>
              홈
            </NavLink>
            <NavLink to="/lives" className={navClass}>
              라이브
            </NavLink>
            <NavLink to="/auctions" className={navClass}>
              경매
            </NavLink>
            {session && (
              <>
                {/* 방송은 판매자 인증을 받은 회원만 만들 수 있어 탭도 인증 판매자에게만 보인다. */}
                {verifiedSeller && (
                  <NavLink to="/studio" className={navClass}>
                    방송하기
                  </NavLink>
                )}
                {/* 상품 판매도 판매자 인증을 받은 회원만 */}
                {verifiedSeller && (
                  <NavLink to="/me/products" className={navClass}>
                    내 상품
                  </NavLink>
                )}
                <NavLink to="/orders" className={navClass}>
                  주문 내역
                </NavLink>
                {session.user.role === "ADMIN" && (
                  <>
                    <NavLink to="/admin/categories" className={navClass}>
                      카테고리 관리
                    </NavLink>
                    <NavLink to="/admin/dm-reports" className={navClass}>
                      채팅 신고
                    </NavLink>
                    <NavLink
                      to="/admin/seller-applications"
                      className={navClass}
                    >
                      판매자 심사
                    </NavLink>
                    <NavLink to="/admin/donation-policy" className={navClass}>
                      도네이션 정책
                    </NavLink>
                  </>
                )}
              </>
            )}
          </nav>
        </div>

        {session ? (
          <div className="flex items-center gap-2">
            <NotificationBell />
            <DmLink />
            <PointBalanceLink />
            <DonationPointLink />
            {/* 내 정보는 탭 대신 닉네임을 눌러 창으로 연다. */}
            <button
              type="button"
              aria-haspopup="dialog"
              aria-label={`${session.user.nickname ?? "내"} 정보 보기`}
              onClick={() => setMyInfoOpen(true)}
              className="rounded-md px-2 py-2 text-body font-semibold hover:bg-neutral-25"
            >
              {session.user.nickname}
            </button>
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
          <div className="flex items-center gap-2">
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
      className="relative flex size-10 items-center justify-center rounded-full border border-neutral-100 text-neutral-700 hover:bg-neutral-25"
    >
      <MessageCircle className="size-[18px]" aria-hidden="true" />
      {count > 0 && (
        <span
          aria-hidden="true"
          className="num absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-500 px-1 text-caption font-bold text-neutral-0"
        >
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}

/** 헤더의 도네이션 포인트 (선물할 수 있는 포인트). 누르면 도네이션 포인트 화면으로 간다. */
function DonationPointLink() {
  const wallet = useQuery({
    queryKey: DONATION_WALLET_KEY,
    queryFn: () => call(api.GET("/api/v1/donation-points/wallet")),
  });
  const balance = wallet.data?.giftableBalance ?? 0;

  return (
    <Link
      to="/me/donation-points"
      className="flex h-10 items-center gap-2 rounded-full border border-neutral-100 px-3.5 font-semibold hover:bg-neutral-25"
      aria-label={
        wallet.data
          ? `도네이션 포인트 ${formatNumber(balance)} P`
          : "도네이션 포인트"
      }
      title="도네이션 포인트 (판매자에게 선물할 수 있는 포인트)"
    >
      <Gift className="size-[18px] text-brand-500" aria-hidden="true" />
      <span className="num">{wallet.data ? formatNumber(balance) : "-"}</span>
      <span className="text-neutral-500">P</span>
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
      className="flex h-10 items-center gap-2 rounded-full border border-neutral-100 px-3.5 font-semibold hover:bg-neutral-25"
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
