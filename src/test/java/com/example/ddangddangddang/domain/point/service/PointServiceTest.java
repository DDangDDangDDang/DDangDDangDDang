package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointHistoryResponse;
import com.example.ddangddangddang.domain.point.dto.PointWalletResponse;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    @InjectMocks
    private PointService pointService;

    @Mock
    private PointWalletRepository pointWalletRepository;

    @Mock
    private PointHistoryRepository pointHistoryRepository;

    @Nested
    @DisplayName("지갑 생성")
    class CreateWallet {

        @Test
        @DisplayName("지갑이 없으면 잔액 0인 지갑을 만든다")
        void success() {
            given(pointWalletRepository.existsByUserId(1L)).willReturn(false);

            pointService.createWallet(1L);

            verify(pointWalletRepository).save(any(PointWallet.class));
        }

        @Test
        @DisplayName("이미 지갑이 있으면 예외가 발생한다")
        void alreadyExists() {
            given(pointWalletRepository.existsByUserId(1L)).willReturn(true);

            assertThatThrownBy(() -> pointService.createWallet(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.WALLET_ALREADY_EXISTS);
            verify(pointWalletRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("지갑 조회")
    class GetWallet {

        @Test
        @DisplayName("사용 가능 포인트, 홀딩 포인트, 합계를 반환한다")
        void success() {
            PointWallet wallet = wallet(10L, 1L);
            ReflectionTestUtils.setField(wallet, "balance", 7_000L);
            ReflectionTestUtils.setField(wallet, "heldBalance", 3_000L);
            given(pointWalletRepository.findByUserId(1L)).willReturn(Optional.of(wallet));

            PointWalletResponse response = pointService.getWallet(1L);

            assertThat(response.balance()).isEqualTo(7_000L);
            assertThat(response.heldBalance()).isEqualTo(3_000L);
            assertThat(response.totalBalance()).isEqualTo(10_000L);
        }

        @Test
        @DisplayName("지갑이 없으면 예외가 발생한다")
        void notFound() {
            given(pointWalletRepository.findByUserId(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.getWallet(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.WALLET_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("포인트 내역 조회")
    class GetHistories {

        @Test
        @DisplayName("size보다 많이 조회되면 size개만 응답하고 마지막 id를 다음 커서로 준다")
        void hasNext() {
            PointWallet wallet = wallet(10L, 1L);
            given(pointWalletRepository.findByUserId(1L)).willReturn(Optional.of(wallet));
            given(pointHistoryRepository.findByCursor(10L, null, null, PageRequest.of(0, 3)))
                    .willReturn(histories(wallet, 5, 4, 3));

            CursorResponse<PointHistoryResponse> response = pointService.getHistories(1L, null, null, 2);

            assertThat(response.content()).extracting(PointHistoryResponse::historyId).containsExactly(5L, 4L);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isEqualTo(4L);
        }

        @Test
        @DisplayName("마지막 페이지면 hasNext가 false이고 다음 커서가 없다")
        void lastPage() {
            PointWallet wallet = wallet(10L, 1L);
            given(pointWalletRepository.findByUserId(1L)).willReturn(Optional.of(wallet));
            given(pointHistoryRepository.findByCursor(10L, null, 4L, PageRequest.of(0, 3)))
                    .willReturn(histories(wallet, 3));

            CursorResponse<PointHistoryResponse> response = pointService.getHistories(1L, null, 4L, 2);

            assertThat(response.content()).hasSize(1);
            assertThat(response.hasNext()).isFalse();
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        @DisplayName("size가 최대값을 넘으면 100개로 제한한다")
        void maxSize() {
            PointWallet wallet = wallet(10L, 1L);
            given(pointWalletRepository.findByUserId(1L)).willReturn(Optional.of(wallet));
            given(pointHistoryRepository.findByCursor(10L, null, null, PageRequest.of(0, 101)))
                    .willReturn(List.of());

            CursorResponse<PointHistoryResponse> response = pointService.getHistories(1L, null, null, 1_000);

            assertThat(response.content()).isEmpty();
        }
    }
    @Nested
    @DisplayName("상위 입찰 (replaceHold)")
    class ReplaceHold {

        @Test
        @DisplayName("이전 입찰자 홀딩을 해제하고 새 입찰자 포인트를 홀딩한다")
        void success() {
            PointWallet prev = walletWithHeld(10L, 1L, 5_000L);
            PointWallet next = walletWithBalance(20L, 2L, 10_000L);
            given(pointWalletRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(prev));
            given(pointWalletRepository.findByUserIdForUpdate(2L)).willReturn(Optional.of(next));

            pointService.replaceHold(2L, 6_000L, 1L, 5_000L, 100L);

            assertThat(prev.getHeldBalance()).isZero();
            assertThat(prev.getBalance()).isEqualTo(5_000L);
            assertThat(next.getHeldBalance()).isEqualTo(6_000L);
            assertThat(next.getBalance()).isEqualTo(4_000L);
            verify(pointHistoryRepository, times(2)).save(any(PointHistory.class));
        }

        @Test
        @DisplayName("새 입찰자 userId가 더 커도 userId가 작은 지갑부터 잠근다")
        void lockOrder() {
            given(pointWalletRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(walletWithBalance(10L, 1L, 10_000L)));
            given(pointWalletRepository.findByUserIdForUpdate(9L)).willReturn(Optional.of(walletWithHeld(90L, 9L, 5_000L)));

            // 새 입찰자 1번, 이전 입찰자 9번 / 새 입찰자 9번, 이전 입찰자 1번 어느 쪽이든 1번부터 잠근다
            pointService.replaceHold(1L, 6_000L, 9L, 5_000L, 100L);

            InOrder inOrder = inOrder(pointWalletRepository);
            inOrder.verify(pointWalletRepository).findByUserIdForUpdate(1L);
            inOrder.verify(pointWalletRepository).findByUserIdForUpdate(9L);
        }

        @Test
        @DisplayName("새 입찰자 포인트가 부족하면 예외가 발생한다")
        void insufficientBalance() {
            given(pointWalletRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(walletWithHeld(10L, 1L, 5_000L)));
            given(pointWalletRepository.findByUserIdForUpdate(2L)).willReturn(Optional.of(walletWithBalance(20L, 2L, 1_000L)));

            assertThatThrownBy(() -> pointService.replaceHold(2L, 6_000L, 1L, 5_000L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    @Nested
    @DisplayName("낙찰, 즉시구매, 정산")
    class PurchaseAndSettle {

        @Test
        @DisplayName("낙찰되면 홀딩 포인트를 차감하고 PURCHASE 내역을 남긴다")
        void purchaseWithHeld() {
            PointWallet wallet = walletWithHeld(10L, 1L, 5_000L);
            given(pointWalletRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(wallet));

            pointService.purchaseWithHeld(1L, 5_000L, 100L);

            assertThat(wallet.getHeldBalance()).isZero();
            verify(pointHistoryRepository).save(any(PointHistory.class));
        }

        @Test
        @DisplayName("구매 확정되면 판매자 지갑에 정산한다")
        void settle() {
            PointWallet seller = walletWithBalance(10L, 1L, 0L);
            given(pointWalletRepository.findByUserIdForUpdate(1L)).willReturn(Optional.of(seller));

            pointService.settle(1L, 5_000L, 200L);

            assertThat(seller.getBalance()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("지갑이 없으면 예외가 발생한다")
        void walletNotFound() {
            given(pointWalletRepository.findByUserIdForUpdate(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> pointService.hold(1L, 1_000L, 100L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(PointErrorCode.WALLET_NOT_FOUND);
        }
    }

    private PointWallet wallet(Long walletId, Long userId) {
        PointWallet wallet = PointWallet.create(userId);
        ReflectionTestUtils.setField(wallet, "id", walletId);
        return wallet;
    }

    private List<PointHistory> histories(PointWallet wallet, long... ids) {
        return LongStream.of(ids).mapToObj(id -> {
            PointHistory history = PointHistory.of(wallet, PointHistoryType.CHARGE, 1_000L, PointReferenceType.CHARGE, id);
            ReflectionTestUtils.setField(history, "id", id);
            return history;
        }).toList();
    }

    private PointWallet walletWithBalance(Long walletId, Long userId, long balance) {
        PointWallet wallet = wallet(walletId, userId);
        ReflectionTestUtils.setField(wallet, "balance", balance);
        return wallet;
    }

    private PointWallet walletWithHeld(Long walletId, Long userId, long heldBalance) {
        PointWallet wallet = wallet(walletId, userId);
        ReflectionTestUtils.setField(wallet, "heldBalance", heldBalance);
        return wallet;
    }
}