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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 1분마다 Redis 조회수를 DB에 옮겨 적고, 끝난 경매를 Redis에서 지우는 일
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

        verify(auctionViewCountService, never()).writeViewCounts(any());
        verify(auctionViewRepository, never()).remove(any());
    }

    @Test
    @DisplayName("DB에 다 적은 다음, 끝난 경매만 Redis 순위표에서 지운다")
    void removesOnlyClosedAuctionsAfterWriting() {
        Map<Long, Long> viewCounts = Map.of(1L, 4L, 13L, 1L);
        given(auctionViewRepository.allViewCounts()).willReturn(viewCounts);
        given(auctionViewCountService.writeViewCounts(viewCounts)).willReturn(List.of(13L));

        auctionScheduler.syncViewCounts();

        // 순서가 중요하다: DB에 먼저 적고, 그다음 지운다
        InOrder order = inOrder(auctionViewCountService, auctionViewRepository);
        order.verify(auctionViewCountService).writeViewCounts(viewCounts);
        order.verify(auctionViewRepository).remove(13L);
        verify(auctionViewRepository, never()).remove(1L);
    }

    @Test
    @DisplayName("DB에 적다가 실패하면 Redis에서 지우지 않는다 (조회수가 사라지지 않게)")
    void keepsRedisWhenWritingFails() {
        Map<Long, Long> viewCounts = Map.of(13L, 1L);
        given(auctionViewRepository.allViewCounts()).willReturn(viewCounts);
        given(auctionViewCountService.writeViewCounts(viewCounts)).willThrow(new RuntimeException("DB 장애"));

        assertThatCode(() -> auctionScheduler.syncViewCounts()).doesNotThrowAnyException();

        verify(auctionViewRepository, never()).remove(any());
    }
}