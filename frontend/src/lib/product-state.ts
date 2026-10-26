import type { Schema } from '@/api';
import type { BadgeProps } from '@/components/ui';

/*
 * 내 상품 목록의 상품 상태 계산. 상품에는 가격이 없고 경매에 올라가야만 팔리므로,
 * 상품 상태(ACTIVE/SOLD)와 가장 최근 경매 상태(auctionStatus)를 함께 보고 화면 문구를 정한다.
 */

type Product = Pick<Schema<'ProductResponse'>, 'status' | 'auctionId' | 'auctionStatus'>;

export type ProductSaleState = {
  label: string;
  variant: NonNullable<BadgeProps['variant']>;
  /** 수정·삭제·경매 일정 잡기를 할 수 있는지. 시작 전·진행 중 경매가 있으면 서버가 막는다. */
  editable: boolean;
};

export function productSaleState(product: Product): ProductSaleState {
  if (product.status === 'SOLD') return { label: '판매 완료', variant: 'neutral', editable: false };
  if (product.auctionStatus === 'READY') return { label: '경매 예정', variant: 'outline', editable: false };
  if (product.auctionStatus === 'ONGOING') return { label: '경매 중', variant: 'live', editable: false };
  // 유찰·취소된 상품: 경매를 안 연 상품과 구분해 보여 준다. 다시 경매에 올려야 팔리므로 수정·삭제·다시 올리기는 그대로 된다.
  if (product.auctionStatus === 'FAILED') return { label: '유찰', variant: 'warning', editable: true };
  if (product.auctionStatus === 'CANCELED') return { label: '취소됨', variant: 'neutral', editable: true };
  // 경매를 아직 열지 않은 상품
  return { label: '판매 준비 중', variant: 'info', editable: true };
}
