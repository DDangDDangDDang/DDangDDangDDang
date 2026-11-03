import { Banknote, Bot, FolderTree, Megaphone, Flag, Gavel, Gift, House, MapPin, Package, ReceiptText, ShoppingBag, Tv, UserCheck, Video, type LucideIcon } from 'lucide-react';
import { NavLink } from 'react-router';
import { useSession } from '@/api';
import { AdBanner } from '@/components/ad/AdBanner';
import { SubscriptionSidebar } from '@/components/seller/SubscriptionSidebar';
import { cn } from '@/lib/cn';
import { useSellerProfile } from '@/lib/use-seller';

/**
 * 왼쪽 메뉴: 화면 이동 + 구독 중인 판매자 + 세로 배너 광고.
 * 헤더 아래에 붙어 따라 내려오고, 메뉴가 길면 메뉴 안에서만 스크롤한다.
 */
export function SideNav() {
  const session = useSession();
  const verifiedSeller = useSellerProfile(session?.user.userId).data?.verifiedSeller ?? false;

  return (
    <div className="sticky top-16 flex h-[calc(100vh-4rem)] w-[240px] shrink-0 flex-col gap-5 overflow-y-auto px-3 pt-4 pb-8">
      <nav aria-label="내 메뉴" className="flex flex-col gap-0.5">
        <Item to="/" end icon={House} label="홈" />
        <Item to="/lives" icon={Tv} label="라이브" />
        <Item to="/auctions" icon={Gavel} label="경매" />
        {session && (
          <>
            {/* 방송과 상품 판매는 판매자 인증을 받은 회원만 할 수 있어 메뉴도 인증 판매자에게만 보인다. */}
            {verifiedSeller && <Item to="/studio" icon={Video} label="방송하기" />}
            {verifiedSeller && <Item to="/me/products" icon={Package} label="내 상품" />}
            <Item to="/orders" icon={ReceiptText} label="주문 내역" />
            <Item to="/me/addresses" icon={MapPin} label="배송지 관리" />
            <Item to="/shop" icon={ShoppingBag} label="마일리지 상점" />
            {session.user.role === 'ADMIN' && (
              <>
                <Divider />
                <p className="px-3 pb-1 text-label font-bold text-neutral-500">관리자</p>
                <Item to="/admin/categories" icon={FolderTree} label="카테고리 관리" />
                <Item to="/admin/dm-reports" icon={Flag} label="채팅 신고" />
                <Item to="/admin/seller-applications" icon={UserCheck} label="판매자 심사" />
                <Item to="/admin/donation-policy" icon={Gift} label="도네이션 정책" />
                <Item to="/admin/point-exchanges" icon={Banknote} label="환전 승인" />
                <Item to="/admin/ads" icon={Megaphone} label="광고 관리" />
                <Item to="/admin/live-bots" icon={Bot} label="라이브 봇" />
              </>
            )}
          </>
        )}
      </nav>
      <Divider />
      <SubscriptionSidebar />
      <AdBanner placement="sidebar" />
    </div>
  );
}

function Item({ to, end, icon: Icon, label }: { to: string; end?: boolean; icon: LucideIcon; label: string }) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cn(
          'flex h-11 items-center gap-3.5 rounded-md px-3 text-body transition-colors',
          isActive ? 'bg-neutral-50 font-bold text-neutral-900' : 'text-neutral-700 hover:bg-neutral-50 hover:text-neutral-900',
        )
      }
    >
      <Icon className="size-5 shrink-0" aria-hidden="true" />
      {label}
    </NavLink>
  );
}

function Divider() {
  return <div aria-hidden="true" className="mx-3 h-px shrink-0 bg-neutral-100" />;
}
