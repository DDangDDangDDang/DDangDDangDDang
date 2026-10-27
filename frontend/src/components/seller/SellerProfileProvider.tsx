import { useState, type ReactNode } from 'react';
import { SellerProfileContext } from './seller-profile-context';
import { SellerProfileDialog } from './SellerProfileDialog';

/** 판매자 프로필 창을 앱 전체에 하나 둔다. (AppLayout) 화면은 useOpenSellerProfile()로 연다. */
export function SellerProfileProvider({ children }: { children: ReactNode }) {
  const [sellerId, setSellerId] = useState<number>();
  return (
    <SellerProfileContext.Provider value={setSellerId}>
      {children}
      {/* 다른 판매자를 열면 탭을 처음(기본 정보)부터 */}
      <SellerProfileDialog key={sellerId} sellerId={sellerId} onClose={() => setSellerId(undefined)} />
    </SellerProfileContext.Provider>
  );
}
