package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.AuctionFixture;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.repository.AuctionViewRepository;
import com.example.ddangddangddang.global.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.ddangddangddang.domain.auction.AuctionFixture.AUCTION_ID;
import static com.example.ddangddangddang.domain.auction.AuctionFixture.SELLER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 경매 상세를 열 때 조회수를 세는 규칙
@ExtendWith(MockitoExtension.class)
class AuctionViewCountingTest {

    private static final Long VIEWER_ID = 2L;
    private static final String IP = "123.45.67.89";
    private static final Duration WINDOW = Duration.ofMinutes(30);

    @InjectMocks
    private AuctionService auctionService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionViewRepository auctionViewRepository;

    // 상세 응답의 이미지 주소를 만들 때 쓴다 (목이라 null을 돌려준다)
    @Mock
    private FileStorage fileStorage;

    @Test
    @DisplayName("다른 사람이 진행 중인 경매를 열면 회원 ID로 조회수를 센다")
    void countsOngoingAuction() {
        givenAuction(AuctionStatus.ONGOING);

        auctionService.findAddBy(AUCTION_ID, VIEWER_ID, IP);

        verify(auctionViewRepository).increaseIfFirstView(AUCTION_ID, "user:" + VIEWER_ID, WINDOW);
    }

    @Test
    @DisplayName("대기 중인 경매도 조회수를 센다")
    void countsReadyAuction() {
        givenAuction(AuctionStatus.READY);

        auctionService.findAddBy(AUCTION_ID, VIEWER_ID, IP);

        verify(auctionViewRepository).increaseIfFirstView(AUCTION_ID, "user:" + VIEWER_ID, WINDOW);
    }

    @Test
    @DisplayName("로그인하지 않았으면 IP로 조회수를 센다")
    void countsByIpWhenNotLoggedIn() {
        givenAuction(AuctionStatus.ONGOING);

        auctionService.findAddBy(AUCTION_ID, null, IP);

        verify(auctionViewRepository).increaseIfFirstView(AUCTION_ID, "ip:" + IP, WINDOW);
    }

    @Test
    @DisplayName("판매자가 자기 경매를 열면 조회수를 세지 않는다")
    void doesNotCountSeller() {
        givenAuction(AuctionStatus.ONGOING);

        auctionService.findAddBy(AUCTION_ID, SELLER_ID, IP);

        verify(auctionViewRepository, never()).increaseIfFirstView(any(), any(), any());
    }

    @Test
    @DisplayName("끝난 경매(낙찰·유찰·취소)는 조회수를 세지 않는다")
    void doesNotCountClosedAuction() {
        givenAuction(AuctionStatus.ENDED);

        auctionService.findAddBy(AUCTION_ID, VIEWER_ID, IP);

        verify(auctionViewRepository, never()).increaseIfFirstView(any(), any(), any());
    }

    @Test
    @DisplayName("Redis가 고장 나도 경매 상세는 정상으로 보여 준다")
    void showsDetailWhenRedisFails() {
        givenAuction(AuctionStatus.ONGOING);
        given(auctionViewRepository.increaseIfFirstView(AUCTION_ID, "user:" + VIEWER_ID, WINDOW))
                .willThrow(new RedisConnectionFailureException("Redis 연결 실패"));

        assertThat(auctionService.findAddBy(AUCTION_ID, VIEWER_ID, IP)).isNotNull();
    }

    // 판매자가 SELLER_ID인 경매를 원하는 상태로 만든다
    private void givenAuction(AuctionStatus status) {
        Auction auction = AuctionFixture.ongoing(LocalDateTime.now());
        ReflectionTestUtils.setField(auction, "status", status);
        given(auctionRepository.findById(AUCTION_ID)).willReturn(Optional.of(auction));
    }
}