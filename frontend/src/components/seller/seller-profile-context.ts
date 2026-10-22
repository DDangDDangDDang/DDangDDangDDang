import { createContext, useContext } from 'react';

/** 판매자 프로필 창을 연다. 앱 전체에 창 하나(SellerProfileProvider, AppLayout)를 두고 어느 화면에서든 연다. */
export const SellerProfileContext = createContext<(sellerId: number) => void>(() => undefined);

export function useOpenSellerProfile() {
  return useContext(SellerProfileContext);
}
