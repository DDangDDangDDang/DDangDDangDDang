package com.example.ddangddangddang.domain.order.commission;

/**
 * 한 거래의 수수료 계산 결과.
 *
 * @param baseRateBps  기본 수수료율(bp)
 * @param discountBps  적용된 할인(bp). 할인이 없으면 0
 * @param discountName 적용된 할인 이름. 할인이 없으면 null
 * @param rateBps      최종 수수료율 = 기본 - 할인 (0 미만이면 0)
 */
public record CommissionQuote(
        int baseRateBps,
        int discountBps,
        String discountName,
        int rateBps
) {
}
