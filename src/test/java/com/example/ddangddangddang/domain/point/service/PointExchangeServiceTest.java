package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointExchangeRequest;
import com.example.ddangddangddang.domain.point.dto.PointExchangeResponse;
import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointExchangeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long EXCHANGE_ID = 100L;

    @InjectMocks
    private PointExchangeService pointExchangeService;

    @Mock
    private PointExchangeRepository pointExchangeRepository;

    @Mock
    private PointExchangeLedger pointExchangeLedger;

    @Nested
    @DisplayName("환전 신청")
    class RequestExchange {

        @Test
        @DisplayName("PENDING으로 저장하고 포인트를 차감한다")
        void success() {
            PointExchangeRequest request = new PointExchangeRequest(10_000L, "088", "110-123-456789", "홍길동");
            given(pointExchangeRepository.save(any(PointExchange.class))).willAnswer(invocation -> {
                PointExchange saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", EXCHANGE_ID);
                return saved;
            });

            PointExchangeResponse response = pointExchangeService.requestExchange(USER_ID, request);

            assertThat(response.exchangeId()).isEqualTo(EXCHANGE_ID);
            assertThat(response.status()).isEqualTo(PointExchangeType.PENDING);
            assertThat(response.amount()).isEqualTo(10_000L);
            assertThat(response.accountNumber()).isEqualTo("110******789");

            ArgumentCaptor<PointExchange> captor = ArgumentCaptor.forClass(PointExchange.class);
            verify(pointExchangeLedger).deduct(captor.capture());
            assertThat(captor.getValue().getId()).isEqualTo(EXCHANGE_ID);
            assertThat(captor.getValue().getPayoutId()).startsWith("point-exchange-");
        }

        @Test
        @DisplayName("금액이 범위를 벗어나면 저장하지도 차감하지도 않는다")
        void outOfRange() {
            PointExchangeRequest request = new PointExchangeRequest(999L, "088", "1101234567", "홍길동");

            assertThatThrownBy(() -> pointExchangeService.requestExchange(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.EXCHANGE_AMOUNT_OUT_OF_RANGE);
            verify(pointExchangeRepository, never()).save(any());
            verify(pointExchangeLedger, never()).deduct(any());
        }

        @Test
        @DisplayName("포인트가 부족하면 예외가 그대로 전파된다 (트랜잭션 롤백)")
        void insufficientPoint() {
            PointExchangeRequest request = new PointExchangeRequest(10_000L, "088", "1101234567", "홍길동");
            given(pointExchangeRepository.save(any(PointExchange.class))).willAnswer(invocation -> invocation.getArgument(0));
            willThrow(new BusinessException(PointErrorCode.INSUFFICIENT_POINT))
                    .given(pointExchangeLedger).deduct(any());

            assertThatThrownBy(() -> pointExchangeService.requestExchange(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INSUFFICIENT_POINT);
        }
    }

    @Nested
    @DisplayName("환전 취소")
    class CancelExchange {

        @Test
        @DisplayName("PENDING이면 CANCELLED로 바꾸고 포인트를 환불한다")
        void success() {
            PointExchange exchange = pendingExchange();
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            PointExchangeResponse response = pointExchangeService.cancelExchange(USER_ID, EXCHANGE_ID);

            assertThat(response.status()).isEqualTo(PointExchangeType.CANCELLED);
            verify(pointExchangeLedger).refund(exchange);
        }

        @Test
        @DisplayName("송금이 시작된 건은 취소할 수 없고 환불하지 않는다")
        void processing() {
            PointExchange exchange = pendingExchange();
            exchange.startProcessing(LocalDateTime.now());
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(exchange));

            assertThatThrownBy(() -> pointExchangeService.cancelExchange(USER_ID, EXCHANGE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INVALID_EXCHANGE_STATUS);
            verify(pointExchangeLedger, never()).refund(any());
        }

        @Test
        @DisplayName("남의 신청은 취소할 수 없다")
        void notOwner() {
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.of(pendingExchange()));

            assertThatThrownBy(() -> pointExchangeService.cancelExchange(2L, EXCHANGE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.EXCHANGE_ACCESS_DENIED);
            verify(pointExchangeLedger, never()).refund(any());
        }

        @Test
        @DisplayName("없는 신청이면 예외가 발생한다")
        void notFound() {
            given(pointExchangeRepository.findByIdForUpdate(EXCHANGE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointExchangeService.cancelExchange(USER_ID, EXCHANGE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.EXCHANGE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("환전 단건 조회")
    class GetExchange {

        @Test
        @DisplayName("남의 신청은 조회할 수 없다")
        void notOwner() {
            given(pointExchangeRepository.findById(EXCHANGE_ID)).willReturn(Optional.of(pendingExchange()));

            assertThatThrownBy(() -> pointExchangeService.getExchange(2L, EXCHANGE_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.EXCHANGE_ACCESS_DENIED);
        }
    }

    private PointExchange pendingExchange() {
        PointExchange exchange = PointExchange.create(USER_ID, 10_000L, "point-exchange-test",
                "088", "1101234567", "홍길동");
        ReflectionTestUtils.setField(exchange, "id", EXCHANGE_ID);
        return exchange;
    }
}
