package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointWalletTest {

    @Test
    @DisplayName("낙찰취소 위약금은 판매자의 사용 가능 포인트를 늘린다")
    void receiveOrderPenalty_increasesBalance() {
        PointWallet wallet = walletWithBalance(1_000L);

        wallet.receiveOrderPenalty(10_000L);

        assertThat(wallet.getBalance()).isEqualTo(11_000L);
    }

    @Test
    @DisplayName("낙찰취소 위약금은 구매자의 사용 가능 포인트에서 뺀다")
    void payOrderPenalty_decreasesBalance() {
        PointWallet wallet = walletWithBalance(100_000L);

        wallet.payOrderPenalty(10_000L);

        assertThat(wallet.getBalance()).isEqualTo(90_000L);
    }

    @Test
    @DisplayName("주문 취소 환불은 사용 가능 포인트만 늘린다")
    void refundOrder_increasesBalanceOnly() {
        PointWallet wallet = walletWithBalance(1_000L);
        wallet.hold(400L);

        wallet.refundOrder(5_000L);

        assertThat(wallet.getBalance()).isEqualTo(5_600L);
        assertThat(wallet.getHeldBalance()).isEqualTo(400L);
    }

    @Test
    @DisplayName("홀딩하면 사용 가능 포인트가 홀딩 포인트로 옮겨진다")
    void hold() {
        PointWallet wallet = walletWithBalance(10_000L);

        wallet.hold(3_000L);

        assertThat(wallet.getBalance()).isEqualTo(7_000L);
        assertThat(wallet.getHeldBalance()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("사용 가능 포인트보다 많이 홀딩하면 예외가 발생하고 잔액은 그대로다")
    void holdInsufficient() {
        PointWallet wallet = walletWithBalance(1_000L);

        assertThatThrownBy(() -> wallet.hold(1_001L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
        assertThat(wallet.getBalance()).isEqualTo(1_000L);
        assertThat(wallet.getHeldBalance()).isZero();
    }

    @Test
    @DisplayName("홀딩을 해제하면 홀딩 포인트가 사용 가능 포인트로 돌아온다")
    void release() {
        PointWallet wallet = walletWithBalance(10_000L);
        wallet.hold(3_000L);

        wallet.release(3_000L);

        assertThat(wallet.getBalance()).isEqualTo(10_000L);
        assertThat(wallet.getHeldBalance()).isZero();
    }

    @Test
    @DisplayName("홀딩 포인트보다 많이 해제하면 예외가 발생한다")
    void releaseMoreThanHeld() {
        PointWallet wallet = walletWithBalance(10_000L);
        wallet.hold(3_000L);

        assertThatThrownBy(() -> wallet.release(3_001L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INSUFFICIENT_HELD_BALANCE);
    }

    @Test
    @DisplayName("낙찰되면 홀딩 포인트에서 차감된다")
    void purchaseWithHeld() {
        PointWallet wallet = walletWithBalance(10_000L);
        wallet.hold(3_000L);

        wallet.purchaseWithHeld(3_000L);

        assertThat(wallet.getBalance()).isEqualTo(7_000L);
        assertThat(wallet.getHeldBalance()).isZero();
    }

    @Test
    @DisplayName("즉시구매하면 사용 가능 포인트에서 바로 차감된다")
    void purchase() {
        PointWallet wallet = walletWithBalance(10_000L);

        wallet.purchase(4_000L);

        assertThat(wallet.getBalance()).isEqualTo(6_000L);
        assertThat(wallet.getHeldBalance()).isZero();
    }

    @Test
    @DisplayName("정산받으면 사용 가능 포인트가 늘어난다")
    void settle() {
        PointWallet wallet = walletWithBalance(0L);

        wallet.settle(5_000L);

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("0 이하 금액은 예외가 발생한다")
    void invalidAmount(long amount) {
        PointWallet wallet = walletWithBalance(10_000L);

        assertThatThrownBy(() -> wallet.hold(amount))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INVALID_AMOUNT);
    }

    @Test
    @DisplayName("환전하면 사용 가능 포인트만 차감되고 홀딩 포인트는 그대로다")
    void exchange() {
        PointWallet wallet = walletWithEarned(10_000L);
        wallet.hold(3_000L);

        wallet.exchange(7_000L);

        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getHeldBalance()).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("홀딩된 포인트까지 환전할 수는 없다")
    void exchangeInsufficient() {
        PointWallet wallet = walletWithEarned(10_000L);
        wallet.hold(3_000L);

        assertThatThrownBy(() -> wallet.exchange(7_001L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
        assertThat(wallet.getBalance()).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("환전 환불하면 사용 가능 포인트로 돌아오고 다시 환전할 수 있다")
    void refundExchange() {
        PointWallet wallet = walletWithEarned(10_000L);
        wallet.exchange(10_000L);

        wallet.refundExchange(10_000L);

        assertThat(wallet.getBalance()).isEqualTo(10_000L);
        assertThat(wallet.getExchangeableBalance()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("충전한 포인트는 환전할 수 없다 (결제 취소로만 돌려받는다)")
    void chargedPointsCannotBeExchanged() {
        PointWallet wallet = walletWithBalance(100_000L);

        assertThat(wallet.getExchangeableBalance()).isZero();
        assertThatThrownBy(() -> wallet.exchange(1_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INSUFFICIENT_EXCHANGEABLE_BALANCE);
        assertThat(wallet.getBalance()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("충전과 정산이 섞여 있으면 정산받은 만큼만 환전할 수 있다")
    void onlyEarnedPointsCanBeExchanged() {
        PointWallet wallet = walletWithBalance(30_000L);
        wallet.settle(20_000L);

        assertThat(wallet.getExchangeableBalance()).isEqualTo(20_000L);
        assertThatThrownBy(() -> wallet.exchange(20_001L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(PointErrorCode.INSUFFICIENT_EXCHANGEABLE_BALANCE);

        wallet.exchange(20_000L);
        assertThat(wallet.getBalance()).isEqualTo(30_000L);
        assertThat(wallet.getExchangeableBalance()).isZero();
    }

    @Test
    @DisplayName("구매에는 충전한 포인트부터 쓰고, 모자란 만큼만 정산받은 포인트에서 쓴다")
    void purchaseSpendsChargedFirst() {
        PointWallet wallet = walletWithBalance(30_000L);
        wallet.settle(20_000L);

        wallet.purchase(25_000L);
        assertThat(wallet.getExchangeableBalance()).isEqualTo(20_000L);

        wallet.purchase(10_000L);
        assertThat(wallet.getBalance()).isEqualTo(15_000L);
        assertThat(wallet.getExchangeableBalance()).isEqualTo(15_000L);
    }

    @Test
    @DisplayName("입찰로 묶였다 풀린 정산 포인트는 다시 환전할 수 있다")
    void holdAndReleaseKeepEarned() {
        PointWallet wallet = walletWithEarned(20_000L);

        wallet.hold(15_000L);
        assertThat(wallet.getExchangeableBalance()).isEqualTo(5_000L);

        wallet.release(15_000L);
        assertThat(wallet.getExchangeableBalance()).isEqualTo(20_000L);
    }

    @Test
    @DisplayName("낙찰로 홀딩 포인트를 쓸 때도 충전한 포인트부터 쓴다")
    void purchaseWithHeldSpendsChargedFirst() {
        PointWallet wallet = walletWithBalance(10_000L);
        wallet.settle(10_000L);
        wallet.hold(15_000L);

        wallet.purchaseWithHeld(15_000L);

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        assertThat(wallet.getExchangeableBalance()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("주문 취소로 돌려받은 포인트는 환전할 수 없는 포인트로 돌아온다")
    void refundOrderIsNotExchangeable() {
        PointWallet wallet = walletWithEarned(10_000L);
        wallet.purchase(10_000L);

        wallet.refundOrder(10_000L);

        assertThat(wallet.getBalance()).isEqualTo(10_000L);
        assertThat(wallet.getExchangeableBalance()).isZero();
    }

    @Test
    @DisplayName("충전 결제가 취소되면 사용 가능 포인트에서 회수하고, 모자라면 있는 만큼만 회수한다")
    void reclaimCharge() {
        PointWallet wallet = walletWithBalance(10_000L);
        wallet.settle(5_000L);
        wallet.hold(12_000L);

        long reclaimed = wallet.reclaimCharge(10_000L);

        assertThat(reclaimed).isEqualTo(3_000L);
        assertThat(wallet.getBalance()).isZero();
        assertThat(wallet.getHeldBalance()).isEqualTo(12_000L);
    }

    @Test
    @DisplayName("충전 회수는 정산받은 포인트를 마지막에 건드린다")
    void reclaimChargeKeepsEarned() {
        PointWallet wallet = walletWithBalance(10_000L);
        wallet.settle(5_000L);

        wallet.reclaimCharge(10_000L);

        assertThat(wallet.getBalance()).isEqualTo(5_000L);
        assertThat(wallet.getExchangeableBalance()).isEqualTo(5_000L);
    }

    private PointWallet walletWithEarned(long earned) {
        PointWallet wallet = PointWallet.create(1L);
        wallet.settle(earned);
        return wallet;
    }

    private PointWallet walletWithBalance(long balance) {
        PointWallet wallet = PointWallet.create(1L);
        if (balance > 0) {
            wallet.charge(balance);
        }
        return wallet;
    }
}