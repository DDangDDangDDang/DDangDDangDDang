package com.example.ddangddangddang.domain.order.commission;

/**
 * 판매 수수료 할인 규칙. 스프링 빈으로 등록하면 CommissionPolicy가 자동으로 모아 적용한다.
 *
 * 예) VIP 판매자 할인, 인기 상품 할인, 이벤트 기간 할인
 * <pre>
 * &#64;Component
 * class VipSellerDiscount implements CommissionDiscount {
 *     public String name() { return "VIP 판매자"; }
 *     public int discountBps(SaleContext sale) { return isVip(sale.sellerId()) ? 200 : 0; } // 2%p 할인
 * }
 * </pre>
 * 여러 규칙이 동시에 맞으면 가장 큰 할인 하나만 적용한다(중복 할인 없음). 규칙은 CommissionPolicy에서 바꿀 수 있다.
 */
public interface CommissionDiscount {

    /** 로그·내역에 남길 할인 이름 */
    String name();

    /** 깎아 줄 수수료율(bp, 100 = 1%p). 해당하지 않으면 0 */
    int discountBps(SaleContext sale);
}
