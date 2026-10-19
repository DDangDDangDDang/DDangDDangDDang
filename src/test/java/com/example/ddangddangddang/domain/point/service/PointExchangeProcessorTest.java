package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import com.example.ddangddangddang.domain.point.infra.PayoutRequest;
import com.example.ddangddangddang.domain.point.infra.PayoutResult;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointExchangeProcessorTest {

    private static final Long EXCHANGE_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);

    @InjectMocks
    private PointExchangeProcessor processor;

    @Mock
    private PointExchangeRepository pointExchangeRepository;

    @Mock
    private PointExchangeLedger pointExchangeLedger;

    @Nested
    @DisplayName("송금 시작")
    class StartProcessing {

        @Test
        @DisplayName("PENDING이면 PROCESSING으로 바꾸고 송금 정보를 돌려준다")
        void success() {
            PointExchange exchange = exchange();
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            Optional<PayoutRequest> request = processor.startProcessing(EXCHANGE_ID, NOW);

            assertThat(exchange.isProcessing()).isTrue();
            assertThat(request).isPresent();
            assertThat(request.get().payoutId()).isEqualTo("point-exchange-test");
            assertThat(request.get().amount()).isEqualTo(10_000L);
            assertThat(request.get().accountNumber()).isEqualTo("1101234567");
        }

        @Test
        @DisplayName("이미 취소된 건이면 송금하지 않는다")
        void cancelled() {
            PointExchange exchange = exchange();
            exchange.cancel();
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            assertThat(processor.startProcessing(EXCHANGE_ID, NOW)).isEmpty();
            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.CANCELLED);
        }

        @Test
        @DisplayName("다른 워커가 이미 가져간 건이면 송금하지 않는다")
        void alreadyProcessing() {
            PointExchange exchange = exchange();
            exchange.startProcessing(NOW);
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            assertThat(processor.startProcessing(EXCHANGE_ID, NOW.plusSeconds(1))).isEmpty();
            assertThat(exchange.getProcessingStartedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("송금 결과 반영")
    class ApplyResult {

        @Test
        @DisplayName("성공이면 COMPLETE, 환불하지 않는다")
        void success() {
            PointExchange exchange = processingExchange();
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            processor.applyResult(EXCHANGE_ID, PayoutResult.success());

            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.COMPLETE);
            verify(pointExchangeLedger, never()).refund(any());
        }

        @Test
        @DisplayName("확실한 실패면 FAILED로 바꾸고 포인트를 환불한다")
        void failed() {
            PointExchange exchange = processingExchange();
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            processor.applyResult(EXCHANGE_ID, PayoutResult.failed("계좌 없음"));

            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.FAILED);
            assertThat(exchange.getFailReason()).isEqualTo("계좌 없음");
            verify(pointExchangeLedger).refund(exchange);
        }

        @Test
        @DisplayName("결과를 모르면 PROCESSING을 유지하고 환불하지 않는다")
        void inProgress() {
            PointExchange exchange = processingExchange();
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            processor.applyResult(EXCHANGE_ID, PayoutResult.inProgress());

            assertThat(exchange.isProcessing()).isTrue();
            verify(pointExchangeLedger, never()).refund(any());
        }

        @Test
        @DisplayName("이미 확정된 건이면 아무것도 하지 않는다 (중복 반영 방지)")
        void alreadyFinished() {
            PointExchange exchange = processingExchange();
            exchange.complete();
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            processor.applyResult(EXCHANGE_ID, PayoutResult.failed("늦게 온 실패"));

            assertThat(exchange.getStatus()).isEqualTo(PointExchangeType.COMPLETE);
            verify(pointExchangeLedger, never()).refund(any());
        }
    }

    private PointExchange exchange() {
        PointExchange exchange = PointExchange.create(1L, 10_000L, "point-exchange-test",
                "088", "1101234567", "홍길동");
        ReflectionTestUtils.setField(exchange, "id", EXCHANGE_ID);
        return exchange;
    }

    private PointExchange processingExchange() {
        PointExchange exchange = exchange();
        exchange.startProcessing(NOW);
        return exchange;
    }
}
