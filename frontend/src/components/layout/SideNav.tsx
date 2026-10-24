import { FolderTree, Flag, Gavel, Gift, House, Package, ReceiptText, Tv, UserCheck, Video, type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router';
import { useSession } from '@/api';
import { AdBanner } from '@/components/ad/AdBanner';
import { SubscriptionSidebar } from '@/components/seller/SubscriptionSidebar';
import { cn } from '@/lib/cn';
import { useSellerProfile } from '@/lib/use-seller';

/**
 * 왼쪽 메뉴: 화면 이동 + 구독 중인 판매자 + 세로 배너 광고.
 * 방송 시청·스튜디오·메시지처럼 본문이 넓어야 하는 화면에서는 접어서(collapsed) 아이콘과 프로필 사진만 보여 준다.
 * 헤더 아래에 붙어 따라 내려오고, 메뉴가 길면 메뉴 안에서만 스크롤한다.
 */
export function SideNav({ collapsed }: { collapsed: boolean }) {
  const session = useSession();
  const verifiedSeller = useSellerProfile(session?.user.userId).data?.verifiedSeller ?? false;

  return (
    <div
      className={cn(
        'sticky top-16 flex h-[calc(100vh-4rem)] shrink-0 flex-col gap-5 overflow-y-auto pt-4 pb-8',
        collapsed ? 'w-[72px] items-center px-3' : 'w-[240px] px-3',
      )}
    >
      <nav aria-label="내 메뉴" className="flex flex-col gap-0.5">
        <Item to="/" end icon={House} label="홈" collapsed={collapsed} />
        <Item to="/lives" icon={Tv} label="라이브" collapsed={collapsed} />
        <Item to="/auctions" icon={Gavel} label="경매" collapsed={collapsed} />
        {session && (
          <>
            {/* 방송과 상품 판매는 판매자 인증을 받은 회원만 할 수 있어 메뉴도 인증 판매자에게만 보인다. */}
            {verifiedSeller && <Item to="/studio" icon={Video} label="방송하기" collapsed={collapsed} />}
            {verifiedSeller && <Item to="/me/products" icon={Package} label="내 상품" collapsed={collapsed} />}
            <Item to="/orders" icon={ReceiptText} label="주문 내역" collapsed={collapsed} />
            {session.user.role === 'ADMIN' && (
              <>
                <Divider collapsed={collapsed} />
                {!collapsed && <p className="px-3 pb-1 text-label font-bold text-neutral-500">관리자</p>}
                <Item to="/admin/categories" icon={FolderTree} label="카테고리 관리" collapsed={collapsed} />
                <Item to="/admin/dm-reports" icon={Flag} label="채팅 신고" collapsed={collapsed} />
                <Item to="/admin/seller-applications" icon={UserCheck} label="판매자 심사" collapsed={collapsed} />
                <Item to="/admin/donation-policy" icon={Gift} label="도네이션 정책" collapsed={collapsed} />
              </>
            )}
          </>
        )}
      </nav>
      <Divider collapsed={collapsed} />
      <SubscriptionSidebar collapsed={collapsed} />
      {!collapsed && <AdBanner placement="sidebar" />}
    </div>
  );
}

function Item({ to, end, icon: Icon, label, collapsed }: { to: string; end?: boolean; icon: LucideIcon; label: string; collapsed: boolean }) {
  return (
    <NavLink
      to={to}
      end={end}
      // 접힌 메뉴에서는 글자를 숨기므로 이름을 aria-label·title로 둔다.
      aria-label={collapsed ? label : undefined}
      title={collapsed ? label : undefined}
      className={({ isActive }) =>
        cn(
          'flex items-center gap-3.5 rounded-md text-body transition-colors',
          collapsed ? 'size-12 justify-center' : 'h-11 px-3',
          isActive ? 'bg-neutral-50 font-bold text-neutral-900' : 'text-neutral-700 hover:bg-neutral-50 hover:text-neutral-900',
        )
      }
    >
      <Icon className="size-5 shrink-0" aria-hidden="true" />
      {!collapsed && label}
    </NavLink>
  );
}

function Divider({ collapsed }: { collapsed: boolean }) {
  return <div aria-hidden="true" className={cn('h-px shrink-0 bg-neutral-100', collapsed ? 'w-8' : 'mx-3')} />;
}
