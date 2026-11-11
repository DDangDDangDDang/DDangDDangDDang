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
import static org.mockito.Mockito.times;
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
        given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PAID, AMOUNT));

        boolean handled = pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(handled).isTrue();
        verify(pointWalletRepository, never()).findByUserIdForUpdate(any());
        verify(pointHistoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("충전 완료 뒤 결제가 전액 취소되면 그만큼 포인트를 회수하고 CHARGE_CANCEL 이력을 남긴다")
    void cancelledAfterCompleted() {
        PointCharge charge = pendingCharge();
        charge.complete();
        PointWallet wallet = wallet();
        wallet.charge(AMOUNT);
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(paymentGateway.getPayment(PAYMENT_ID))
                .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.CANCELLED, AMOUNT, AMOUNT));
        given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

        pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(wallet.getBalance()).isZero();
        assertThat(charge.getCancelledAmount()).isEqualTo(AMOUNT);
        verify(pointHistoryRepository).save(any());
    }

    @Test
    @DisplayName("같은 취소 웹훅이 다시 와도 두 번 회수하지 않고, 부분 취소는 새로 취소된 만큼만 회수한다")
    void partialCancelIsIdempotent() {
        PointCharge charge = pendingCharge();
        charge.complete();
        PointWallet wallet = wallet();
        wallet.charge(AMOUNT);
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
        given(paymentGateway.getPayment(PAYMENT_ID))
                .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PARTIAL_CANCELLED, AMOUNT, 3_000L))
                .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PARTIAL_CANCELLED, AMOUNT, 3_000L))
                .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.CANCELLED, AMOUNT, AMOUNT));

        pointChargeService.syncChargeFromWebhook(PAYMENT_ID);
        pointChargeService.syncChargeFromWebhook(PAYMENT_ID);
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT - 3_000L);

        pointChargeService.syncChargeFromWebhook(PAYMENT_ID);
        assertThat(wallet.getBalance()).isZero();
        assertThat(charge.getCancelledAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("입찰에 묶여 회수하지 못한 취소분은 남겨 두고, 입찰이 풀리면 마저 회수한다")
    void shortfallIsCollectedLater() {
        PointCharge charge = pendingCharge();
        charge.complete();
        PointWallet wallet = wallet();
        wallet.charge(AMOUNT);
        wallet.hold(AMOUNT);
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
        given(paymentGateway.getPayment(PAYMENT_ID))
                .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.CANCELLED, AMOUNT, AMOUNT));
        PointChargeProcessor processor =
                new PointChargeProcessor(pointChargeRepository, pointWalletRepository, pointHistoryRepository);

        pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(charge.getCancelledAmount()).isEqualTo(AMOUNT);
        assertThat(charge.getUnreclaimedAmount()).isEqualTo(AMOUNT);
        assertThat(wallet.getReclaimDebt()).isEqualTo(AMOUNT);
        verify(pointHistoryRepository, never()).save(any());

        // 아직 쓸 수 있는 포인트가 없으면 회수하지 않는다
        assertThat(processor.collectUnreclaimed(PAYMENT_ID)).isZero();

        wallet.release(AMOUNT);
        assertThat(wallet.getSpendableBalance()).isZero();

        assertThat(processor.collectUnreclaimed(PAYMENT_ID)).isEqualTo(AMOUNT);
        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getReclaimDebt()).isZero();
        assertThat(charge.getUnreclaimedAmount()).isZero();
        org.mockito.ArgumentCaptor<com.example.ddangddangddang.domain.point.entity.PointHistory> history =
                org.mockito.ArgumentCaptor.forClass(com.example.ddangddangddang.domain.point.entity.PointHistory.class);
        verify(pointHistoryRepository).save(history.capture());
        assertThat(history.getValue().getType())
                .isEqualTo(com.example.ddangddangddang.domain.point.entity.PointHistoryType.CHARGE_CANCEL);
        assertThat(history.getValue().getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("결제 확인 보정: PENDING인데 포트원에 결제 완료면 포인트를 지급하고, 결제가 없으면 그대로 둔다")
    void recoverPending() {
        PointCharge paidCharge = pendingCharge();
        PointWallet wallet = wallet();
        givenCharge(paidCharge);
        given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PAID, AMOUNT));
        given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

        pointChargeService.recoverPending(PAYMENT_ID);

        assertThat(paidCharge.getStatus()).isEqualTo(PointChargeType.COMPLETE);
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
    }

    @Test
    @DisplayName("결제 확인 보정: 포트원에 결제가 없으면(결제창에서 결제 안 함) 예외 없이 PENDING으로 둔다")
    void recoverPendingNotPaidYet() {
        PointCharge charge = pendingCharge();
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(charge));
        given(paymentGateway.getPayment(PAYMENT_ID))
                .willThrow(new com.example.ddangddangddang.global.exception.BusinessException(
                        com.example.ddangddangddang.domain.point.exception.PointErrorCode.PAYMENT_NOT_FOUND));

        pointChargeService.recoverPending(PAYMENT_ID);

        assertThat(charge.getStatus()).isEqualTo(PointChargeType.PENDING);
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

    @Test
    @DisplayName("지급 전에 부분 취소된 결제의 웹훅이면 결제 금액을 지급한 뒤 취소분을 회수한다")
    void partiallyCancelledBeforeCharge() {
        PointCharge charge = pendingCharge();
        PointWallet wallet = wallet();
        givenCharge(charge);
        given(paymentGateway.getPayment(PAYMENT_ID))
                .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PARTIAL_CANCELLED, AMOUNT, 3_000L));
        given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

        boolean handled = pointChargeService.syncChargeFromWebhook(PAYMENT_ID);

        assertThat(handled).isTrue();
        assertThat(charge.getStatus()).isEqualTo(PointChargeType.COMPLETE);
        assertThat(wallet.getBalance()).isEqualTo(AMOUNT - 3_000L);
        assertThat(charge.getCancelledAmount()).isEqualTo(3_000L);
        verify(pointHistoryRepository, times(2)).save(any());
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
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
