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

    private PointWallet walletWithBalance(long balance) {
        PointWallet wallet = PointWallet.create(1L);
        if (balance > 0) {
            wallet.charge(balance);
        }
        return wallet;
    }
}