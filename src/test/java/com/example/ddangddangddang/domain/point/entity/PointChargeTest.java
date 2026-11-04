package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointChargeTest {

    private static final String PAYMENT_ID = "point-charge-test";

    @Nested
    @DisplayName("충전 요청 생성")
    class Create {

        @Test
        @DisplayName("PENDING 상태로 생성된다")
        void success() {
            PointCharge charge = PointCharge.create(1L, 10_000L, PAYMENT_ID);

            assertThat(charge.getUserId()).isEqualTo(1L);
            assertThat(charge.getAmount()).isEqualTo(10_000L);
            assertThat(charge.getPaymentId()).isEqualTo(PAYMENT_ID);
            assertThat(charge.isPending()).isTrue();
        }

        @Test
        @DisplayName("userId가 없으면 예외가 발생한다")
        void nullUserId() {
            assertThatThrownBy(() -> PointCharge.create(null, 10_000L, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("금액이 없거나 0 이하면 예외가 발생한다")
        void invalidAmount() {
            assertThatThrownBy(() -> PointCharge.create(1L, null, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);
            assertThatThrownBy(() -> PointCharge.create(1L, 0L, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_AMOUNT);
        }

        @Test
        @DisplayName("한 번에 1,000P 미만이나 1,000,000P 초과로는 충전할 수 없다")
        void amountOutOfRange() {
            assertThatThrownBy(() -> PointCharge.create(1L, 999L, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE);
            assertThatThrownBy(() -> PointCharge.create(1L, 1_000_001L, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.CHARGE_AMOUNT_OUT_OF_RANGE);
            assertThat(PointCharge.create(1L, 1_000L, PAYMENT_ID).getAmount()).isEqualTo(1_000L);
            assertThat(PointCharge.create(1L, 1_000_000L, PAYMENT_ID).getAmount()).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("완료되지 않은 충전은 결제가 취소돼도 회수할 금액이 없고, 회수 금액은 충전 금액을 넘지 않는다")
        void newlyCancelled() {
            PointCharge charge = PointCharge.create(1L, 10_000L, PAYMENT_ID);
            assertThat(charge.newlyCancelled(10_000L)).isZero();

            charge.complete();
            assertThat(charge.newlyCancelled(4_000L)).isEqualTo(4_000L);
            charge.recordCancellation(4_000L);
            assertThat(charge.newlyCancelled(4_000L)).isZero();
            assertThat(charge.newlyCancelled(99_000L)).isEqualTo(6_000L);
        }

        @Test
        @DisplayName("paymentId가 비어 있으면 예외가 발생한다")
        void blankPaymentId() {
            assertThatThrownBy(() -> PointCharge.create(1L, 10_000L, " "))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_PAYMENT_ID);
        }
    }

    @Nested
    @DisplayName("상태 변경")
    class ChangeStatus {

        @Test
        @DisplayName("PENDING이면 COMPLETE로 바꿀 수 있다")
        void complete() {
            PointCharge charge = PointCharge.create(1L, 10_000L, PAYMENT_ID);

            charge.complete();

            assertThat(charge.isCompleted()).isTrue();
            assertThat(charge.isPending()).isFalse();
        }

        @Test
        @DisplayName("PENDING이면 FAILED로 바꿀 수 있다")
        void fail() {
            PointCharge charge = PointCharge.create(1L, 10_000L, PAYMENT_ID);

            charge.fail();

            assertThat(charge.getStatus()).isEqualTo(PointChargeType.FAILED);
        }

        @Test
        @DisplayName("이미 처리된 요청은 다시 상태를 바꿀 수 없다")
        void notPending() {
            PointCharge charge = PointCharge.create(1L, 10_000L, PAYMENT_ID);
            charge.complete();

            assertThatThrownBy(charge::complete)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_CHARGE_STATUS);
            assertThatThrownBy(charge::fail)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_CHARGE_STATUS);
        }
    }

    @Nested
    @DisplayName("검증")
    class Validate {

        @Test
        @DisplayName("본인 요청이면 통과하고, 남의 요청이면 예외가 발생한다")
        void validateOwner() {
            PointCharge charge = PointCharge.create(1L, 10_000L, PAYMENT_ID);

            charge.validateOwner(1L);
            assertThatThrownBy(() -> charge.validateOwner(2L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.CHARGE_ACCESS_DENIED);
        }

        @Test
        @DisplayName("결제 금액이 요청 금액과 같은지 판단한다")
        void matchesAmount() {
            PointCharge charge = PointCharge.create(1L, 10_000L, PAYMENT_ID);

            assertThat(charge.matchesAmount(10_000L)).isTrue();
            assertThat(charge.matchesAmount(1_000L)).isFalse();
        }
    }
}