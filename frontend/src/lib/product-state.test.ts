import { describe, expect, it } from 'vitest';
import { productSaleState } from './product-state';

describe('productSaleState', () => {
  it('시작 전·진행 중 경매가 있으면 경매 예정·경매 중이고 수정·삭제할 수 없다', () => {
    expect(productSaleState({ status: 'ACTIVE', auctionId: 1, auctionStatus: 'READY' })).toEqual({ label: '경매 예정', variant: 'outline', editable: false });
    expect(productSaleState({ status: 'ACTIVE', auctionId: 1, auctionStatus: 'ONGOING' })).toEqual({ label: '경매 중', variant: 'live', editable: false });
  });

  it('팔린 상품은 판매 완료이고 수정·삭제할 수 없다', () => {
    expect(productSaleState({ status: 'SOLD', auctionId: 1, auctionStatus: 'ENDED' })).toMatchObject({ label: '판매 완료', editable: false });
  });

  it('경매가 없으면 판매 준비 중, 유찰·취소면 유찰·취소됨이고 모두 수정·삭제할 수 있다', () => {
    expect(productSaleState({ status: 'ACTIVE', auctionStatus: undefined })).toEqual({ label: '판매 준비 중', variant: 'info', editable: true });
    expect(productSaleState({ status: 'ACTIVE', auctionId: 1, auctionStatus: 'FAILED' })).toEqual({ label: '유찰', variant: 'warning', editable: true });
    expect(productSaleState({ status: 'ACTIVE', auctionId: 1, auctionStatus: 'CANCELED' })).toEqual({ label: '취소됨', variant: 'neutral', editable: true });
  });
});
