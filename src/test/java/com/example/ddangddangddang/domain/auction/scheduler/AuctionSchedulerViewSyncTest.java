package com.example.ddangddangddang.domain.auction.scheduler;

import com.example.ddangddangddang.domain.auction.repository.AuctionViewRepository;
import com.example.ddangddangddang.domain.auction.service.AuctionViewCountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 1분마다 Redis에 모인 조회수를 DB에 더하고, 더한 만큼 Redis에서 빼는 일
@ExtendWith(MockitoExtension.class)
class AuctionSchedulerViewSyncTest {

    @InjectMocks
    private AuctionScheduler auctionScheduler;

    @Mock
    private AuctionViewRepository auctionViewRepository;

    @Mock
    private AuctionViewCountService auctionViewCountService;

    @Test
    @DisplayName("조회수가 하나도 없으면 DB에 아무것도 하지 않는다")
    void doesNothingWhenNoViews() {
        given(auctionViewRepository.allViewCounts()).willReturn(Map.of());

        auctionScheduler.syncViewCounts();

         verify(auctionViewCountService, never()).addViewCounts(any());
         verify(auctionViewRepository, never()).subtract(any());
    }

    @Test
    @DisplayName("DB에 다 더한 다음, 더한 만큼 Redis에서 뺀다")
    void subtractsAfterAdding() {
        Map<Long, Long> viewCounts = Map.of(1L, 4L, 13L, 1L);
        given(auctionViewRepository.allViewCounts()).willReturn(viewCounts);

        auctionScheduler.syncViewCounts();

        // 순서가 중요하다: DB에 먼저 더하고, 그다음 뺀다
        InOrder order = inOrder(auctionViewCountService, auctionViewRepository);
        order.verify(auctionViewCountService).addViewCounts(viewCounts);
        order.verify(auctionViewRepository).subtract(viewCounts);
    }

    @Test
    @DisplayName("DB에 더하다가 실패하면 Redis에서 빼지 않는다 (조회수가 사라지지 않게)")
    void keepsRedisWhenWritingFails() {
        Map<Long, Long> viewCounts = Map.of(13L, 1L);
        given(auctionViewRepository.allViewCounts()).willReturn(viewCounts);
        willThrow(new RuntimeException("DB 장애")).given(auctionViewCountService).addViewCounts(viewCounts);
        assertThatCode(() -> auctionScheduler.syncViewCounts()).doesNotThrowAnyException();

        verify(auctionViewRepository, never()).subtract(any());
    }
}