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
import com.example.ddangddangddang.domain.point.infra.PaymentGateway;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.infra.PaymentStatus;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointChargeServiceTest {

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

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        PointChargeProcessor processor =
                new PointChargeProcessor(pointChargeRepository, pointWalletRepository, pointHistoryRepository);
        pointChargeService = new PointChargeService(pointChargeRepository, paymentGateway, userRepository, processor);
    }

    @Nested
    @DisplayName("충전 요청 생성")
    class CreateChargeRequest {

        @Test
        @DisplayName("PENDING 충전 요청을 저장하고 결제창에 필요한 값을 반환한다")
        void success() {
            PointChargeRequest request = new PointChargeRequest();
            ReflectionTestUtils.setField(request, "amount", AMOUNT);
            User buyer = User.create("buyer@test.com", "pw", "구매자", "01012345678");
            given(userRepository.findById(USER_ID)).willReturn(Optional.of(buyer));
            given(pointChargeRepository.save(any(PointCharge.class)))
                    .willAnswer(invocation -> {
                        PointCharge saved = invocation.getArgument(0);
                        ReflectionTestUtils.setField(saved, "id", 100L);
                        return saved;
                    });

            PointChargeResponse response = pointChargeService.createChargeRequest(USER_ID, request);

            assertThat(response.chargeId()).isEqualTo(100L);
            assertThat(response.paymentId()).startsWith("charge-");
            assertThat(response.paymentId()).hasSizeLessThanOrEqualTo(40);
            assertThat(response.amount()).isEqualTo(AMOUNT);
            assertThat(response.orderName()).isEqualTo("10,000 포인트 충전");
            // KG이니시스 등에서 필수인 구매자 정보
            assertThat(response.customer().fullName()).isEqualTo("구매자");
            assertThat(response.customer().email()).isEqualTo("buyer@test.com");
            assertThat(response.customer().phoneNumber()).isEqualTo("01012345678");
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
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, AMOUNT));
            given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

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
            given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointChargeService.completeCharge(USER_ID, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.CHARGE_NOT_FOUND);
            verify(paymentGateway, never()).getPayment(anyString());
        }

        @Test
        @DisplayName("남의 충전 요청이면 포트원을 조회하지 않고 예외가 발생한다")
        void notOwner() {
            givenCharge(pendingCharge());

            assertThatThrownBy(() -> pointChargeService.completeCharge(2L, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.CHARGE_ACCESS_DENIED);
            verify(paymentGateway, never()).getPayment(anyString());
        }

        @Test
        @DisplayName("이미 완료된 요청이면 포인트를 다시 지급하지 않고 결과만 반환한다")
        void alreadyCompleted() {
            PointCharge charge = pendingCharge();
            charge.complete();
            PointWallet wallet = wallet();
            ReflectionTestUtils.setField(wallet, "balance", AMOUNT);
            givenCharge(charge);
            given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.COMPLETE);
            assertThat(response.balance()).isEqualTo(AMOUNT);
            assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
            verify(paymentGateway, never()).getPayment(anyString());
            verify(pointHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("결제가 아직 진행 중이면 PENDING을 유지한다")
        void inProgress() {
            PointCharge charge = pendingCharge();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID))
                    .willReturn(payment(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED, AMOUNT));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.PENDING);
            assertThat(response.balance()).isNull();
            verify(pointWalletRepository, never()).findByUserIdForUpdate(anyLong());
        }

        @Test
        @DisplayName("결제가 실패했으면 FAILED로 바꾸고 포인트를 지급하지 않는다")
        void paymentFailed() {
            PointCharge charge = pendingCharge();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.FAILED, AMOUNT));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.FAILED);
            verify(pointWalletRepository, never()).findByUserIdForUpdate(anyLong());
            verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
        }

        @Test
        @DisplayName("결제 금액이 다르면 결제를 취소하고 FAILED로 바꾼다")
        void amountMismatch() {
            PointCharge charge = pendingCharge();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, 1_000L));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.FAILED);
            verify(pointWalletRepository, never()).findByUserIdForUpdate(anyLong());
            // 취소는 락을 걸기 전(트랜잭션 밖)에 끝낸다
            InOrder order = inOrder(paymentGateway, pointChargeRepository);
            order.verify(paymentGateway).cancelPayment(eq(PAYMENT_ID), anyString());
            order.verify(pointChargeRepository).findByPaymentIdForUpdate(PAYMENT_ID);
        }

        @Test
        @DisplayName("금액이 다른 결제의 취소가 실패하면 PENDING으로 남겨 다음 조회 때 다시 처리한다")
        void amountMismatchCancelFailed() {
            PointCharge charge = pendingCharge();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, 1_000L));
            willThrow(new BusinessException(PointErrorCode.PAYMENT_GATEWAY_ERROR))
                    .given(paymentGateway).cancelPayment(eq(PAYMENT_ID), anyString());

            assertThatThrownBy(() -> pointChargeService.completeCharge(USER_ID, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class);
            assertThat(charge.isPending()).isTrue();
            verify(pointChargeRepository, never()).findByPaymentIdForUpdate(anyString());
        }

        @Test
        @DisplayName("포트원을 조회하는 사이 다른 경로(웹훅)가 먼저 완료했으면 포인트를 다시 지급하지 않는다")
        void completedByOtherPathWhileQuerying() {
            PointCharge beforeLock = pendingCharge();
            PointCharge afterLock = pendingCharge();
            afterLock.complete();
            PointWallet wallet = wallet();
            ReflectionTestUtils.setField(wallet, "balance", AMOUNT);
            given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(beforeLock));
            given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, AMOUNT));
            given(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).willReturn(Optional.of(afterLock));
            given(pointWalletRepository.findByUserId(USER_ID)).willReturn(Optional.of(wallet));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.COMPLETE);
            assertThat(response.balance()).isEqualTo(AMOUNT);
            assertThat(wallet.getBalance()).isEqualTo(AMOUNT);
            verify(pointWalletRepository, never()).findByUserIdForUpdate(anyLong());
            verify(pointHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("지갑이 없으면 예외가 발생한다")
        void walletNotFound() {
            givenCharge(pendingCharge());
            given(paymentGateway.getPayment(PAYMENT_ID)).willReturn(payment(PaymentStatus.PAID, AMOUNT));
            given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointChargeService.completeCharge(USER_ID, PAYMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.WALLET_NOT_FOUND);
        }
    }

    // 포인트를 지급하기 전에 포트원 콘솔에서 부분 취소된 결제 (서버 장애·지연 사이에 관리자가 일부 환불한 경우)
    @Nested
    @DisplayName("지급 전에 취소된 결제")
    class CancelledBeforeCharge {

        @Test
        @DisplayName("부분 취소된 결제면 결제 금액을 지급한 뒤 취소분을 회수해, 실제로 낸 금액만큼만 남긴다")
        void partiallyCancelled() {
            PointCharge charge = pendingCharge();
            PointWallet wallet = wallet();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID))
                    .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PARTIAL_CANCELLED, AMOUNT, 3_000L));
            given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.COMPLETE);
            assertThat(response.balance()).isEqualTo(7_000L);
            assertThat(wallet.getBalance()).isEqualTo(7_000L);
            assertThat(charge.getCancelledAmount()).isEqualTo(3_000L);
            assertThat(charge.getUnreclaimedAmount()).isZero();

            // 내역: 충전 +10,000 → 결제 취소 회수 3,000 (무슨 일이 있었는지 그대로 남는다)
            ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
            verify(pointHistoryRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(PointHistory::getType, PointHistory::getAmount)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(PointHistoryType.CHARGE, AMOUNT),
                            org.assertj.core.groups.Tuple.tuple(PointHistoryType.CHARGE_CANCEL, 3_000L));
            // 관리자가 일부만 돌려준 결제라 남은 금액까지 취소하지 않는다
            verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
        }

        @Test
        @DisplayName("부분 취소로 처리한 충전을 다시 확인해도(프론트·웹훅·보정) 다시 지급·회수하지 않는다")
        void partiallyCancelledIsIdempotent() {
            PointCharge charge = pendingCharge();
            PointWallet wallet = wallet();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID))
                    .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PARTIAL_CANCELLED, AMOUNT, 3_000L));
            given(pointWalletRepository.findByUserIdForUpdate(USER_ID)).willReturn(Optional.of(wallet));
            lenient().when(pointWalletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

            pointChargeService.completeCharge(USER_ID, PAYMENT_ID);
            PointChargeCompleteResponse again = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(again.status()).isEqualTo(PointChargeType.COMPLETE);
            assertThat(again.balance()).isEqualTo(7_000L);
            assertThat(wallet.getBalance()).isEqualTo(7_000L);
            verify(pointHistoryRepository, times(2)).save(any());
            verify(paymentGateway, times(1)).getPayment(PAYMENT_ID);
        }

        @Test
        @DisplayName("부분 취소됐는데 결제 총액이 요청 금액과 다르면 남은 금액을 취소하고 FAILED로 바꾼다")
        void partiallyCancelledAmountMismatch() {
            PointCharge charge = pendingCharge();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID))
                    .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.PARTIAL_CANCELLED, 1_000L, 500L));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.FAILED);
            verify(paymentGateway).cancelPayment(eq(PAYMENT_ID), anyString());
            verify(pointWalletRepository, never()).findByUserIdForUpdate(anyLong());
        }

        @Test
        @DisplayName("전액 취소된 결제면 FAILED로 바꾸고 포인트를 지급하지 않는다")
        void fullyCancelled() {
            PointCharge charge = pendingCharge();
            givenCharge(charge);
            given(paymentGateway.getPayment(PAYMENT_ID))
                    .willReturn(new PaymentInfo(PAYMENT_ID, PaymentStatus.CANCELLED, AMOUNT, AMOUNT));

            PointChargeCompleteResponse response = pointChargeService.completeCharge(USER_ID, PAYMENT_ID);

            assertThat(response.status()).isEqualTo(PointChargeType.FAILED);
            verify(pointWalletRepository, never()).findByUserIdForUpdate(anyLong());
            verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
        }
    }

    // 처음엔 락 없이 읽고, 결과를 반영할 때(PointChargeProcessor) 락을 걸고 다시 읽는다.
    // 포트원 조회 전에 끝나는 경우(본인 아님·이미 처리됨)엔 두 번째 읽기가 없다.
    private void givenCharge(PointCharge charge) {
        given(pointChargeRepository.findByPaymentId(PAYMENT_ID)).willReturn(Optional.of(charge));
        lenient().when(pointChargeRepository.findByPaymentIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(charge));
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