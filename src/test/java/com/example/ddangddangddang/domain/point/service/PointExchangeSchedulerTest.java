package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.infra.PayoutClient;
import com.example.ddangddangddang.domain.point.infra.PayoutRequest;
import com.example.ddangddangddang.domain.point.infra.PayoutResult;
import com.example.ddangddangddang.domain.point.infra.PayoutStatus;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointExchangeSchedulerTest {

    private static final Long EXCHANGE_ID = 100L;
    private static final PayoutRequest REQUEST =
            new PayoutRequest("point-exchange-test", 10_000L, "088", "1101234567", "홍길동");

    @Mock
    private PointExchangeRepository pointExchangeRepository;

    @Mock
    private PointExchangeProcessor processor;

    @Mock
    private PayoutClient payoutClient;

    private PointExchangeScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PointExchangeScheduler(pointExchangeRepository, processor, payoutClient, 10);
    }

    @Nested
    @DisplayName("PENDING 처리")
    class ProcessOne {

        @Test
        @DisplayName("송금을 요청하고 결과를 반영한다")
        void success() {
            given(processor.startProcessing(eq(EXCHANGE_ID), any(LocalDateTime.class))).willReturn(Optional.of(REQUEST));
            given(payoutClient.requestPayout(REQUEST)).willReturn(PayoutResult.success());

            scheduler.processOne(EXCHANGE_ID);

            verify(processor).applyResult(EXCHANGE_ID, PayoutResult.success());
        }

        @Test
        @DisplayName("이미 처리된 건이면 송금을 요청하지 않는다")
        void skip() {
            given(processor.startProcessing(eq(EXCHANGE_ID), any(LocalDateTime.class))).willReturn(Optional.empty());

            scheduler.processOne(EXCHANGE_ID);

            verify(payoutClient, never()).requestPayout(any());
            verify(processor, never()).applyResult(any(), any());
        }

        @Test
        @DisplayName("송금 요청 중 예외가 나면 실패가 아니라 결과 미확정으로 반영한다")
        void exceptionMeansUnknown() {
            given(processor.startProcessing(eq(EXCHANGE_ID), any(LocalDateTime.class))).willReturn(Optional.of(REQUEST));
            given(payoutClient.requestPayout(REQUEST)).willThrow(new RuntimeException("timeout"));

            scheduler.processOne(EXCHANGE_ID);

            ArgumentCaptor<PayoutResult> captor = ArgumentCaptor.forClass(PayoutResult.class);
            verify(processor).applyResult(eq(EXCHANGE_ID), captor.capture());
            assertThat(captor.getValue().status()).isEqualTo(PayoutStatus.IN_PROGRESS);
        }
    }

    @Nested
    @DisplayName("PENDING 일괄 처리")
    class ProcessPending {

        @Test
        @DisplayName("한 건에서 예외가 나도 다음 건은 계속 처리한다")
        void continueOnError() {
            given(pointExchangeRepository.findIdsByStatus(any(), any())).willReturn(List.of(1L, 2L));
            given(processor.startProcessing(eq(1L), any(LocalDateTime.class))).willThrow(new RuntimeException("db"));
            given(processor.startProcessing(eq(2L), any(LocalDateTime.class))).willReturn(Optional.of(REQUEST));
            given(payoutClient.requestPayout(REQUEST)).willReturn(PayoutResult.success());

            scheduler.processPending();

            verify(processor).applyResult(2L, PayoutResult.success());
        }
    }

    @Nested
    @DisplayName("PROCESSING 복구")
    class RecoverOne {

        @Test
        @DisplayName("송금사에 결과가 있으면 그 결과로 확정한다")
        void found() {
            given(processor.findProcessing(EXCHANGE_ID)).willReturn(Optional.of(REQUEST));
            given(payoutClient.getPayout(REQUEST.payoutId())).willReturn(PayoutResult.success());

            scheduler.recoverOne(EXCHANGE_ID);

            verify(payoutClient, never()).requestPayout(any());
            verify(processor).applyResult(EXCHANGE_ID, PayoutResult.success());
        }

        @Test
        @DisplayName("송금사에 기록이 없으면 같은 payoutId로 다시 요청한다")
        void notFoundRetries() {
            given(processor.findProcessing(EXCHANGE_ID)).willReturn(Optional.of(REQUEST));
            given(payoutClient.getPayout(REQUEST.payoutId())).willReturn(PayoutResult.notFound());
            given(payoutClient.requestPayout(REQUEST)).willReturn(PayoutResult.success());

            scheduler.recoverOne(EXCHANGE_ID);

            verify(payoutClient).requestPayout(REQUEST);
            verify(processor).applyResult(EXCHANGE_ID, PayoutResult.success());
        }

        @Test
        @DisplayName("이미 확정된 건이면 아무것도 하지 않는다")
        void skip() {
            given(processor.findProcessing(EXCHANGE_ID)).willReturn(Optional.empty());

            scheduler.recoverOne(EXCHANGE_ID);

            verify(payoutClient, never()).getPayout(any());
            verify(processor, never()).applyResult(any(), any());
        }
    }
}
