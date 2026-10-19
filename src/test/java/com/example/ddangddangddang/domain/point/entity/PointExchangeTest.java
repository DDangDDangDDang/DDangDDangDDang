package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointExchangeTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    private PointExchange create(long amount) {
        return PointExchange.create(1L, amount, "point-exchange-test", "088", "110-123-456789", "홍길동");
    }

    @Nested
    @DisplayName("환전 신청 생성")
    class Create {

        @Test
        @DisplayName("PENDING 상태로 생성되고 계좌번호는 숫자만 저장된다")
        void success() {
            PointExchange exchange = create(10_000L);

            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.PENDING);
            assertThat(exchange.getAccountNumber()).isEqualTo("110123456789");
            assertThat(exchange.getAmount()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("최소·최대 금액 경계값은 허용된다")
        void boundary() {
            assertThat(create(PointExchange.MIN_AMOUNT).getAmount()).isEqualTo(1_000L);
            assertThat(create(PointExchange.MAX_AMOUNT).getAmount()).isEqualTo(1_000_000L);
        }

        @Test
        @DisplayName("1,000 미만이나 1,000,000 초과면 예외가 발생한다")
        void outOfRange() {
            assertThatThrownBy(() -> create(999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.EXCHANGE_AMOUNT_OUT_OF_RANGE);
            assertThatThrownBy(() -> create(1_000_001L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.EXCHANGE_AMOUNT_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("금액이 없으면 예외가 발생한다")
        void nullAmount() {
            assertThatThrownBy(() -> PointExchange.create(1L, null, "id", "088", "1101234567", "홍길동"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.EXCHANGE_AMOUNT_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("userId가 없으면 예외가 발생한다")
        void nullUserId() {
            assertThatThrownBy(() -> PointExchange.create(null, 10_000L, "id", "088", "1101234567", "홍길동"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.INVALID_INPUT);
        }

        @Test
        @DisplayName("계좌번호에 숫자가 아닌 문자가 있으면 예외가 발생한다")
        void invalidAccount() {
            assertThatThrownBy(() -> PointExchange.create(1L, 10_000L, "id", "088", "110-abc-456", "홍길동"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_BANK_ACCOUNT);
        }

        @Test
        @DisplayName("예금주가 비어 있으면 예외가 발생한다")
        void blankHolder() {
            assertThatThrownBy(() -> PointExchange.create(1L, 10_000L, "id", "088", "1101234567", " "))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_BANK_ACCOUNT);
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class Transition {

        @Test
        @DisplayName("PENDING → PROCESSING → COMPLETE")
        void complete() {
            PointExchange exchange = create(10_000L);

            exchange.startProcessing(NOW);
            assertThat(exchange.isProcessing()).isTrue();
            assertThat(exchange.getProcessingStartedAt()).isEqualTo(NOW);

            exchange.complete();
            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.COMPLETE);
        }

        @Test
        @DisplayName("PROCESSING → FAILED, 사유가 기록된다")
        void fail() {
            PointExchange exchange = create(10_000L);
            exchange.startProcessing(NOW);

            exchange.fail("계좌 없음");

            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.FAILED);
            assertThat(exchange.getFailReason()).isEqualTo("계좌 없음");
        }

        @Test
        @DisplayName("PENDING이면 취소할 수 있다")
        void cancel() {
            PointExchange exchange = create(10_000L);

            exchange.cancel();

            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.CANCELLED);
        }

        @Test
        @DisplayName("송금이 시작된 뒤에는 취소할 수 없다")
        void cannotCancelWhileProcessing() {
            PointExchange exchange = create(10_000L);
            exchange.startProcessing(NOW);

            assertThatThrownBy(exchange::cancel)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
        }

        @Test
        @DisplayName("송금 시작 전에는 완료·실패 처리할 수 없다")
        void cannotFinishBeforeProcessing() {
            PointExchange exchange = create(10_000L);

            assertThatThrownBy(exchange::complete)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
            assertThatThrownBy(() -> exchange.fail("x"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
        }

        @Test
        @DisplayName("취소된 건은 송금을 시작할 수 없다")
        void cancelledCannotStart() {
            PointExchange exchange = create(10_000L);
            exchange.cancel();

            assertThatThrownBy(() -> exchange.startProcessing(NOW))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
        }
    }

    @Test
    @DisplayName("본인 신청이면 통과하고 남의 신청이면 예외가 발생한다")
    void validateOwner() {
        PointExchange exchange = create(10_000L);

        exchange.validateOwner(1L);
        assertThatThrownBy(() -> exchange.validateOwner(2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.EXCHANGE_ACCESS_DENIED);
    }
}
