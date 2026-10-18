package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointExchangeLedgerTest {

    private static final Long USER_ID = 1L;

    @InjectMocks
    private PointExchangeLedger ledger;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Test
    @DisplayName("차감: 잔액이 줄고 EXCHANGE 이력이 환전 id와 함께 남는다")
    void deduct() {
        PointWallet wallet = wallet(50_000L);
        given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

        ledger.deduct(exchange());

        assertThat(wallet.getBalance()).isEqualTo(40_000L);
        PointHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(PointHistoryType.EXCHANGE);
        assertThat(history.getAmount()).isEqualTo(10_000L);
        assertThat(history.getRefType()).isEqualTo(PointReferenceType.EXCHANGE);
        assertThat(history.getRefId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("차감: 잔액이 부족하면 예외가 나고 이력을 남기지 않는다")
    void deductInsufficient() {
        given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet(5_000L)));

        assertThatThrownBy(() -> ledger.deduct(exchange()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INSUFFICIENT_POINT);
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("환불: 잔액이 늘고 EXCHANGE_REFUND 이력이 남는다")
    void refund() {
        PointWallet wallet = wallet(40_000L);
        given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

        ledger.refund(exchange());

        assertThat(wallet.getBalance()).isEqualTo(50_000L);
        PointHistory history = savedHistory();
        assertThat(history.getType()).isEqualTo(PointHistoryType.EXCHANGE_REFUND);
        assertThat(history.getRefId()).isEqualTo(100L);
    }

    private PointHistory savedHistory() {
        ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
        verify(pointHistoryRepository).save(captor.capture());
        return captor.getValue();
    }

    private PointWallet wallet(long balance) {
        PointWallet wallet = PointWallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "id", 10L);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    private PointExchange exchange() {
        PointExchange exchange = PointExchange.create(USER_ID, 10_000L, "point-exchange-test",
                "088", "1101234567", "홍길동");
        ReflectionTestUtils.setField(exchange, "id", 100L);
        return exchange;
    }
}
