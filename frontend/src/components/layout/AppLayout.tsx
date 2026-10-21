import { useQuery } from '@tanstack/react-query';
import { Gavel, MessageCircle, Wallet } from 'lucide-react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router';
import { api, call, logout, useSession } from '@/api';
import { BroadcastProvider } from '@/components/broadcast/BroadcastProvider';
import { MiniBroadcast } from '@/components/broadcast/MiniBroadcast';
import { DmRealtime } from '@/components/dm/DmRealtime';
import { Button, buttonVariants } from '@/components/ui';
import { cn } from '@/lib/cn';
import { formatNumber } from '@/lib/format';

/**
 * 모든 화면 공통 틀: 상단 헤더 + 가운데 정렬된 본문 (최대 폭 1360px)
 * 판매자 송출(BroadcastProvider)을 여기에 두어 방송 중에 다른 화면으로 가도 송출이 이어지고, 작은 방송 창이 뜬다.
 */
export function AppLayout() {
  const session = useSession();
  return (
    <BroadcastProvider>
      <div className="min-h-screen">
        {session?.user.userId !== undefined && <DmRealtime userId={session.user.userId} />}
        <AppHeader />
        <main className="mx-auto max-w-[1360px] px-8 pt-6 pb-14">
          <Outlet />
        </main>
        <MiniBroadcast />
      </div>
    </BroadcastProvider>
  );
}

const navClass = ({ isActive }: { isActive: boolean }) =>
  cn('rounded-md px-3 py-2 text-body font-semibold transition-colors', isActive ? 'text-neutral-900' : 'text-neutral-500 hover:text-neutral-900');

function AppHeader() {
  const session = useSession();
  const navigate = useNavigate();

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
          {session && (
            <nav aria-label="내 메뉴" className="flex items-center">
              <NavLink to="/lives" className={navClass}>
                라이브
              </NavLink>
              <NavLink to="/auctions" className={navClass}>
                경매
              </NavLink>
              <NavLink to="/studio" className={navClass}>
                방송하기
              </NavLink>
              <NavLink to="/me/products" className={navClass}>
                내 상품
              </NavLink>
              <NavLink to="/orders" className={navClass}>
                주문 내역
              </NavLink>
              <NavLink to="/me" end className={navClass}>
                내 정보
              </NavLink>
              {session.user.role === 'ADMIN' && (
                <>
                  <NavLink to="/admin/categories" className={navClass}>
                    카테고리 관리
                  </NavLink>
                  <NavLink to="/admin/dm-reports" className={navClass}>
                    채팅 신고
                  </NavLink>
                </>
              )}
            </nav>
          )}
        </div>

        {session ? (
          <div className="flex items-center gap-2">
            <DmLink />
            <PointBalanceLink />
            <span className="px-2 text-body font-semibold">{session.user.nickname}</span>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                // 서버 요청이 실패해도 logout()이 이 화면의 로그인 상태는 지운다. 어떤 경우든 홈으로 간다.
                void logout()
                  .catch(() => undefined)
                  .finally(() => navigate('/'));
              }}
            >
              로그아웃
            </Button>
          </div>
        ) : (
          <div className="flex items-center gap-2">
            <Link to="/login" className={buttonVariants({ variant: 'ghost', size: 'sm' })}>
              로그인
            </Link>
            <Link to="/signup" className={buttonVariants({ variant: 'primary', size: 'sm' })}>
              회원가입
            </Link>
          </div>
        )}
      </div>
    </header>
  );
}

/** 헤더의 1:1 채팅 바로가기 + 읽지 않은 메시지 수 */
function DmLink() {
  const unread = useQuery({
    queryKey: ['dm', 'unread'],
    queryFn: () => call(api.GET('/api/v1/dm/unread-count')),
  });
  const count = unread.data?.unreadCount ?? 0;

  return (
    <Link
      to="/messages"
      aria-label={count > 0 ? `메시지, 읽지 않은 메시지 ${count}개` : '메시지'}
      className="relative flex size-10 items-center justify-center rounded-full border border-neutral-100 text-neutral-700 hover:bg-neutral-25"
    >
      <MessageCircle className="size-[18px]" aria-hidden="true" />
      {count > 0 && (
        <span aria-hidden="true" className="num absolute -top-1 -right-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand-500 px-1 text-caption font-bold text-neutral-0">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </Link>
  );
}

/** 헤더의 사용 가능 포인트. 누르면 포인트 화면으로 간다. */
function PointBalanceLink() {
  const wallet = useQuery({
    queryKey: ['points', 'wallet'],
    queryFn: () => call(api.GET('/api/v1/points/wallet')),
  });

  return (
    <Link
      to="/points"
      className="flex h-10 items-center gap-2 rounded-full border border-neutral-100 px-3.5 font-semibold hover:bg-neutral-25"
      aria-label={wallet.data ? `사용 가능 포인트 ${formatNumber(wallet.data.balance ?? 0)} P` : '포인트'}
    >
      <Wallet className="size-[18px] text-neutral-500" aria-hidden="true" />
      <span className="num">{wallet.data ? formatNumber(wallet.data.balance ?? 0) : '-'}</span>
      <span className="text-neutral-500">P</span>
    </Link>
  );
}
