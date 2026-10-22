package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.dto.response.BidResponse;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BidFacadeTest {

    private static final BidResponse SUCCESS = new BidResponse(1L, 100L, 10_000L, 1, LocalDateTime.now(), false);

    @InjectMocks
    private BidFacade bidFacade;

    @Mock
    private BidService bidService;

    @Test
    @DisplayName("다른 입찰에 밀려 실패하면 새 트랜잭션으로 재시도한다")
    void retryOnConflict() {
        given(bidService.tryPlaceBid(2L, 100L, 10_000L))
                .willReturn(Optional.empty(), Optional.empty(), Optional.of(SUCCESS));

        assertThat(bidFacade.placeBid(2L, 100L, 10_000L)).isEqualTo(SUCCESS);
        verify(bidService, times(3)).tryPlaceBid(2L, 100L, 10_000L);
    }

    @Test
    @DisplayName("최대 횟수까지 계속 밀리면 BID_CONFLICT")
    void giveUp() {
        given(bidService.tryPlaceBid(2L, 100L, 10_000L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bidFacade.placeBid(2L, 100L, 10_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.BID_CONFLICT);
        verify(bidService, times(BidFacade.MAX_ATTEMPTS)).tryPlaceBid(2L, 100L, 10_000L);
    }

    @Test
    @DisplayName("재조회 결과 내 입찰이 더는 유효하지 않으면 재시도 없이 그 이유로 끝난다")
    void invalidAfterRetry() {
        given(bidService.tryPlaceBid(2L, 100L, 10_000L))
                .willReturn(Optional.empty())
                .willThrow(new BusinessException(AuctionErrorCode.BID_TOO_LOW));

        assertThatThrownBy(() -> bidFacade.placeBid(2L, 100L, 10_000L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuctionErrorCode.BID_TOO_LOW);
        verify(bidService, times(2)).tryPlaceBid(2L, 100L, 10_000L);
    }

    @Test
    @DisplayName("락 대기 초과 같은 락 예외도 재시도한다")
    void retryOnLockFailure() {
        given(bidService.tryPlaceBid(2L, 100L, 10_000L))
                .willThrow(new CannotAcquireLockException("lock wait timeout"))
                .willReturn(Optional.of(SUCCESS));

        assertThat(bidFacade.placeBid(2L, 100L, 10_000L)).isEqualTo(SUCCESS);
    }
}
