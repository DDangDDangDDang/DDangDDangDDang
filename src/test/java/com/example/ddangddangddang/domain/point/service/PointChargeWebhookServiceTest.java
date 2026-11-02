package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointChargeType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.infra.PaymentGateway;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.infra.PaymentStatus;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 웹훅 경로(syncChargeFromWebhook) 전용 테스트. 프론트 경로(completeCharge)는 PointChargeServiceTest에 있다.
 */
@ExtendWith(MockitoExtension.class)
class PointChargeWebhookServiceTest {

    private static final Long USER_ID = 1L;
    private static final String PAYMENT_ID = "point-charge-test";
    private static final long AMOUNT = 10_000L;

    private PointChargeService pointChargeService;

    @Mock
    private PointChargeRepository pointChargeRepository;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @BeforeEach
    void setUp() {
        PointChargeProcessor processor =
                new PointChargeProcessor(pointChargeRepository, pointWalletRepository, pointHistoryRepository);
        pointChargeService = new PointChargeService(pointChargeRepository, paymentGateway, null, processor);
    }

    @Test
    @DisplayName("결제 완료 웹훅이면 본인 확인 없이 포트원 재조회 후 포인트를 지급한다")
    void paid() {
        PointCharge charge = pendingCharge();
        PointWallet wallet = wallet();
        givenCharge(charge);
        given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PAID, AMOUNT));
        given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

        boolean handled = pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(handled).isTrue();
        assertThat(charge.getStatus()).isEqualTo(PointChargeType.COMPLETE);
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
        verify(pointHistoryRepository).save(any());
    }

    @Test
    @DisplayName("우리 충전 요청이 아닌 결제면 포트원을 조회하지 않고 무시한다")
    void unknownPayment() {
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());

        boolean handled = pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(handled).isFalse();
        verify(paymentGateway, never()).getPayment(anyString());
    }

    @Test
    @DisplayName("프론트 경로로 이미 완료된 건이면 포인트를 다시 지급하지 않는다")
    void alreadyCompleted() {
        PointCharge charge = pendingCharge();
        charge.complete();
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(charge));

        boolean handled = pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(handled).isTrue();
        verify(paymentGateway, never()).getPayment(anyString());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("웹훅 본문과 관계없이 포트원 조회 결과가 실패면 FAILED로 처리한다")
    void failedByPortOne() {
        PointCharge charge = pendingCharge();
        givenCharge(charge);
        given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.FAILED, AMOUNT));

        pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(charge.getStatus()).isEqualTo(PointChargeType.FAILED);
        verify(pointWalletRepository, never()).findByUserIdForUpdate(any());
    }

    @Test
    @DisplayName("결제창만 열린 상태(READY) 웹훅이면 PENDING을 유지한다")
    void ready() {
        PointCharge charge = pendingCharge();
        givenCharge(charge);
        given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.READY, AMOUNT));

        pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(charge.isPending()).isTrue();
    }

    // 처음엔 락 없이 읽고, 결과를 반영할 때(PointChargeProcessor) 락을 걸고 다시 읽는다
    private void givenCharge(PointCharge charge) {
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
    }

    private PointCharge pendingCharge() {
        PointCharge charge = PointCharge.create(USER_ID, AMOUNT, PAYMENT_ID);
        ReflectionTestUtils.setField(charge, "id", 100L);
        return charge;
    }

    private PointWallet wallet() {
        PointWallet wallet = PointWallet.create(USER_ID);
        ReflectionTestUtils.setField(wallet, "id", 10L);
        return wallet;
    }
}
