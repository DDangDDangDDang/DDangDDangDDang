package com.example.ddangddangddang.domain.order.commission;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 판매 수수료 정책. 판매가에서 수수료를 떼고 나머지를 판매자에게 정산한다. (기본 5%: 10,000P 판매 → 9,500P 정산)
 *
 * - 기본 수수료율: 설정 order.commission.rate-bps (bp 단위, 500 = 5%)
 * - 할인: CommissionDiscount 빈을 모두 모아, 가장 큰 할인 하나를 기본 수수료율에서 뺀다 (0 미만이면 0)
 * - 수수료율은 주문이 생길 때(낙찰·즉시구매) 주문에 저장한다. 나중에 정책이 바뀌어도 이미 생긴 주문은 그대로다
 */
@Slf4j
@Component
public class CommissionPolicy {

    public static final int BPS_DENOMINATOR = 10_000;

    private final int baseRateBps;
    private final List<CommissionDiscount> discounts;

    public CommissionPolicy(@Value("${order.commission.rate-bps:500}") int baseRateBps,
                            List<CommissionDiscount> discounts) {
        if (baseRateBps < 0 || baseRateBps >= BPS_DENOMINATOR) {
            throw new IllegalArgumentException("order.commission.rate-bps는 0 이상 10000 미만이어야 합니다: " + baseRateBps);
        }
        this.baseRateBps = baseRateBps;
        this.discounts = List.copyOf(discounts);
    }

    /** 이 거래의 수수료율과 할인 내역 */
    public CommissionQuote quote(SaleContext sale) {
        record Applied(String name, int bps) {
        }
        Applied best = discounts.stream()
                .map(d -> new Applied(d.name(), Math.max(0, d.discountBps(sale))))
                .filter(a -> a.bps() > 0)
                .max(Comparator.comparingInt(Applied::bps))
                .orElse(null);

        int discount = best == null ? 0 : Math.min(best.bps(), baseRateBps);
        CommissionQuote quote = new CommissionQuote(baseRateBps, discount, best == null ? null : best.name(), baseRateBps - discount);
        if (discount > 0) {
            log.info("판매 수수료 할인 적용 auctionId={}, sellerId={}, discount={}, {}bp → {}bp",
                    sale.auctionId(), sale.sellerId(), quote.discountName(), baseRateBps, quote.rateBps());
        }
        return quote;
    }

    /** 이 거래의 최종 수수료율(bp) */
    public int rateBpsFor(SaleContext sale) {
        return quote(sale).rateBps();
    }

    /** 수수료. 원 미만은 버린다 (판매자에게 유리하게). 예) 5%에 10,010P → 500P */
    public static long commissionOf(long price, int rateBps) {
        return price * rateBps / BPS_DENOMINATOR;
    }
}
