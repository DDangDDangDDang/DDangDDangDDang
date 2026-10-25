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

  it('경매가 없거나 유찰·취소면 판매 준비 중이고 수정·삭제할 수 있다', () => {
    for (const auctionStatus of [undefined, 'FAILED', 'CANCELED'] as const) {
      expect(productSaleState({ status: 'ACTIVE', auctionStatus })).toEqual({ label: '판매 준비 중', variant: 'info', editable: true });
    }
  });
});
