package com.example.ddangddangddang.domain.order.commission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionPolicyTest {

    private static final SaleContext SALE = new SaleContext(100L, 2L, 1L, 10_000L);

    private static CommissionDiscount discount(String name, int bps) {
        return new CommissionDiscount() {
            public String name() {
                return name;
            }

            public int discountBps(SaleContext sale) {
                return bps;
            }
        };
    }

    @Test
    @DisplayName("할인이 없으면 기본 수수료율 5%")
    void base() {
        CommissionQuote quote = new CommissionPolicy(500, List.of()).quote(SALE);

        assertThat(quote.rateBps()).isEqualTo(500);
        assertThat(quote.discountBps()).isZero();
        assertThat(quote.discountName()).isNull();
    }

    @Test
    @DisplayName("여러 할인이 맞으면 가장 큰 할인 하나만 적용한다 (중복 할인 없음)")
    void bestDiscountOnly() {
        CommissionPolicy policy = new CommissionPolicy(500, List.of(discount("인기 상품", 100), discount("VIP 판매자", 200), discount("해당 없음", 0)));

        CommissionQuote quote = policy.quote(SALE);

        assertThat(quote.rateBps()).isEqualTo(300);
        assertThat(quote.discountName()).isEqualTo("VIP 판매자");
    }

    @Test
    @DisplayName("할인이 기본 수수료율보다 커도 0% 아래로 내려가지 않는다")
    void notBelowZero() {
        CommissionPolicy policy = new CommissionPolicy(500, List.of(discount("전액 면제", 9_000)));

        assertThat(policy.rateBpsFor(SALE)).isZero();
    }

    @Test
    @DisplayName("수수료는 원 미만을 버린다 (5%: 10,010P → 500P)")
    void commissionFloor() {
        assertThat(CommissionPolicy.commissionOf(10_000L, 500)).isEqualTo(500L);
        assertThat(CommissionPolicy.commissionOf(10_010L, 500)).isEqualTo(500L);
        assertThat(CommissionPolicy.commissionOf(10_000L, 0)).isZero();
    }

    @Test
    @DisplayName("기본 수수료율 설정은 0 이상 100% 미만이어야 서버가 뜬다")
    void range() {
        assertThatThrownBy(() -> new CommissionPolicy(-1, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommissionPolicy(10_000, List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
