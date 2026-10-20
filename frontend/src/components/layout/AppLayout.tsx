import { Gavel } from 'lucide-react';
import { Link, Outlet } from 'react-router';
import { logout, useSession } from '@/api';
import { Button } from '@/components/ui';

/** 모든 화면 공통 틀: 상단 헤더 + 가운데 정렬된 본문 (최대 폭 1360px) */
export function AppLayout() {
  return (
    <div className="min-h-screen">
      <AppHeader />
      <main className="mx-auto max-w-[1360px] px-8 pt-6 pb-14">
        <Outlet />
      </main>
    </div>
  );
}

function AppHeader() {
  const session = useSession();

  return (
    <header className="border-b border-neutral-100 bg-neutral-0">
      <div className="mx-auto flex h-16 max-w-[1360px] items-center justify-between gap-4 px-8">
        <Link to="/" className="flex items-center gap-2.5">
          <span className="flex size-8 items-center justify-center rounded-md bg-brand-500 text-neutral-0">
            <Gavel className="size-[18px]" aria-hidden="true" />
          </span>
          <span className="text-[20px] font-bold tracking-tight">땅땅땅</span>
        </Link>

        {session ? (
          <div className="flex items-center gap-2">
            <span className="text-body font-semibold">{session.user.nickname}</span>
            <Button variant="ghost" size="sm" onClick={() => void logout()}>
              로그아웃
            </Button>
          </div>
        ) : (
          // 로그인 화면은 3단계에서 만든다.
          <span className="text-label text-neutral-500">로그인하지 않음</span>
        )}
      </div>
    </header>
  );
}
