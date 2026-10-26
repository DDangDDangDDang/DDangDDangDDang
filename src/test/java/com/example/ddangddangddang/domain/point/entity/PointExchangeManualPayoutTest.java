package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointExchangeManualPayoutTest {

    private static final Long ADMIN_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 23, 12, 0);

    private PointExchange pending() {
        return PointExchange.create(1L, 10_000L, "point-exchange-test", "088", "110-123-456789", "홍길동");
    }

    private PointExchange processing() {
        PointExchange exchange = pending();
        exchange.startManualPayout(ADMIN_ID, NOW);
        return exchange;
    }

    @Test
    @DisplayName("송금 시작: PROCESSING이 되고 처리한 관리자가 남는다. 이후 사용자 취소는 막힌다")
    void start() {
        PointExchange exchange = processing();

        assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.PROCESSING);
        assertThat(exchange.getProcessingStartedAt()).isEqualTo(NOW);
        assertThat(exchange.getHandledBy()).isEqualTo(ADMIN_ID);
        assertThatThrownBy(exchange::cancel)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
    }

    @Test
    @DisplayName("지급 완료: 송금 시작 없이 바로 완료할 수 없다")
    void completeRequiresStart() {
        assertThatThrownBy(() -> pending().completeManualPayout(ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
    }

    @Test
    @DisplayName("지급 완료: PROCESSING → COMPLETE")
    void complete() {
        PointExchange exchange = processing();

        exchange.completeManualPayout(ADMIN_ID);

        assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.COMPLETE);
    }

    @Test
    @DisplayName("반려: PENDING과 PROCESSING 모두 FAILED가 되고 사유가 남는다")
    void reject() {
        PointExchange beforeStart = pending();
        PointExchange afterStart = processing();

        beforeStart.reject(ADMIN_ID, " 예금주가 일치하지 않습니다 ");
        afterStart.reject(ADMIN_ID, "이체 실패");

        assertThat(beforeStart.getStatus()).isEqualTo(PointExchangeType.FAILED);
        assertThat(beforeStart.getFailReason()).isEqualTo("예금주가 일치하지 않습니다");
        assertThat(beforeStart.getHandledBy()).isEqualTo(ADMIN_ID);
        assertThat(afterStart.getStatus()).isEqualTo(PointExchangeType.FAILED);
    }

    @Test
    @DisplayName("반려: 이미 지급 완료된 건은 반려할 수 없다 (포인트 이중 환불 방지)")
    void rejectCompleted() {
        PointExchange exchange = processing();
        exchange.completeManualPayout(ADMIN_ID);

        assertThatThrownBy(() -> exchange.reject(ADMIN_ID, "실수"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
    }

    @Test
    @DisplayName("반려: 사유가 없으면 예외")
    void rejectWithoutReason() {
        assertThatThrownBy(() -> pending().reject(ADMIN_ID, " "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);
    }
}
