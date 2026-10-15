package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointChargeCompleteResponse;
import com.example.ddangddangddang.domain.point.dto.PointChargeRequest;
import com.example.ddangddangddang.domain.point.dto.PointChargeResponse;
import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointChargeType;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.infra.PaymentClient;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.infra.PaymentStatus;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointChargeServiceTest {

    private static final Long USER_ID = 1L;
    private static final String PAYMENT_ID = "point-charge-test";
    private static final long AMOUNT = 10_000L;

    @InjectMocks
    private PointChargeService pointChargeService;

    @Mock
    private PointChargeRepository pointChargeRepository;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Mock
    private PaymentClient paymentClient;

    @Nested
    @DisplayName("충전 요청 생성")
    class CreateChargeRequest {

        @Test
        @DisplayName("PENDING 충전 요청을 저장하고 결제창에 필요한 값을 반환한다")
        void success() {
            PointChargeRequest request = new PointChargeRequest();
            ReflectionTestUtils.setField(request, "amount", AMOUNT);
            given(pointChargeRepository.save(any(PointCharge.class)))
                    .willAnswer(invocation -> {
                        PointCharge saved = invocation.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 100L);
                        return saved;
                    });

            PointChargeResponse response = pointChargeService.createChargeRequest(USER_ID, request);

            assertThat(response.chargeId()).isEqualTo(100L);
            assertThat(response.paymentId()).startsWith("point-charge-");
            assertThat(response.amount()).isEqualTo(AMOUNT);
            assertThat(response.orderName()).isEqualTo("10,000 포인트 충전");
        }
    }

    @Nested
    @DisplayName("충전 완료 처리")
    class CompleteCharge {

        @Test
        @DisplayName("결제 완료 + 금액 일치면 포인트를 지급하고 이력을 남긴다")
        void success() {
            PointCharge charge = pendingCharge();
            PointWallet wallet = wallet();
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
            given(paymentClient.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, AMOUNT));
            given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.COMPLETE);
            assertThat(response.balance()).isEqualTo(AMOUNT);
            assertThat(wallet.getBalance()).isEqualTo(AMOUNT);

            ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointHistoryRepository).save(captor.capture());
            PointHistory history = captor.getValue();
            assertThat(history.getType()).isEqualTo(PointHistoryType.CHARGE);
            assertThat(history.getAmount()).isEqualTo(AMOUNT);
            assertThat(history.getRefType()).isEqualTo(PointReferenceType.CHARGE);
            assertThat(history.getRefId()).isEqualTo(charge.getId());
        }

        @Test
        @DisplayName("충전 요청이 없으면 예외가 발생한다")
        void chargeNotFound() {
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointChargeService.completeCharge(USER_ID, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.CHARGE_NOT_FOUND);
            verify(paymentClient, never()).getPayment(anyString());
        }

        @Test
        @DisplayName("남의 충전 요청이면 포트원을 조회하지 않고 예외가 발생한다")
        void notOwner() {
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID))
                    .willReturn(Optional.of(pendingCharge()));

            assertThatThrownBy(() -> pointChargeService.completeCharge(2L, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.CHARGE_ACCESS_DENIED);
            verify(paymentClient, never()).getPayment(anyString());
        }

        @Test
        @DisplayName("이미 완료된 요청이면 포인트를 다시 지급하지 않고 결과만 반환한다")
        void alreadyCompleted() {
            PointCharge charge = pendingCharge();
            charge.complete();
            PointWallet wallet = wallet();
            ReflectionTestUtils.setField(wallet, "balance", AMOUNT);
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
            given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.COMPLETE);
            assertThat(response.balance()).isEqualTo(AMOUNT);
            assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
            verify(paymentClient, never()).getPayment(anyString());
            verify(pointHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("결제가 아직 진행 중이면 PENDING을 유지한다")
        void inProgress() {
            PointCharge charge = pendingCharge();
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
            given(paymentClient.getPayment(PAYMENT_ID))
                    .willReturn(payment(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED, AMOUNT));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.PENDING);
            assertThat(response.balance()).isNull();
            verify(pointWalletRepository, never()).findByUserId(anyLong());
        }

        @Test
        @DisplayName("결제가 실패했으면 FAILED로 바꾸고 포인트를 지급하지 않는다")
        void paymentFailed() {
            PointCharge charge = pendingCharge();
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
            given(paymentClient.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.FAILED, AMOUNT));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.FAILED);
            verify(pointWalletRepository, never()).findByUserId(anyLong());
            verify(paymentClient, never()).cancelPayment(anyString(), anyString());
        }

        @Test
        @DisplayName("결제 금액이 다르면 결제를 취소하고 FAILED로 바꾼다")
        void amountMismatch() {
            PointCharge charge = pendingCharge();
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(charge));
            given(paymentClient.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, 1_000L));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.FAILED);
            verify(paymentClient).cancelPayment(eq(PAYMENT_ID), anyString());
            verify(pointWalletRepository, never()).findByUserId(anyLong());
        }

        @Test
        @DisplayName("지갑이 없으면 예외가 발생한다")
        void walletNotFound() {
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID))
                    .willReturn(Optional.of(pendingCharge()));
            given(paymentClient.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, AMOUNT));
            given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointChargeService.completeCharge(USER_ID, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.WALLET_NOT_FOUND);
        }
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

    private PaymentInfo payment(PaymentStatus status, long totalAmount) {
        return new PaymentInfo(PAYMENT_ID, status, totalAmount);
    }
}