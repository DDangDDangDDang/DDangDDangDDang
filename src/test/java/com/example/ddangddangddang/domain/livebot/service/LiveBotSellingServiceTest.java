package com.example.ddangddangddang.domain.livebot.service;

import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.service.AuctionService;
import com.example.ddangddangddang.domain.livebot.entity.LiveBot;
import com.example.ddangddangddang.domain.livebot.exception.LiveBotErrorCode;
import com.example.ddangddangddang.domain.livebot.repository.LiveBotRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.order.dto.OrderShipRequest;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.service.OrderService;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.service.ProductService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 봇으로 물건 팔기: 각 도메인 서비스를 봇 계정 ID로 부르는지, 봇 방송이 꺼져 있거나 봇이 없으면 막는지 본다. */
@ExtendWith(MockitoExtension.class)
class LiveBotSellingServiceTest {

    private static final Long BOT_ID = 3L;
    private static final Long USER_ID = 30L;
    private static final Long LIVE_ID = 100L;
    private static final String KEY = "live-bots/123e4567-e89b-12d3-a456-426614174000.mp4";

    @InjectMocks
    private LiveBotSellingService sellingService;

    @Mock
    private LiveBotRepository liveBotRepository;
    @Mock
    private LiveStreamRepository liveStreamRepository;
    @Mock
    private LiveStreamService liveStreamService;
    @Mock
    private ProductService productService;
    @Mock
    private AuctionService auctionService;
    @Mock
    private OrderService orderService;

    private static void assertError(Executable executable, ErrorCode code) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    private void givenBot() {
        LiveBot bot = LiveBot.create(USER_ID, KEY);
        ReflectionTestUtils.setField(bot, "id", BOT_ID);
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.of(bot));
    }

    private void givenLive() {
        LiveStream live = LiveStream.createBot(USER_ID, "봇 방송", null, KEY);
        ReflectionTestUtils.setField(live, "id", LIVE_ID);
        live.start(USER_ID);
        given(liveStreamRepository.findByActiveSellerId(USER_ID)).willReturn(Optional.of(live));
    }

    @Test
    @DisplayName("상품 등록: 봇 계정을 판매자로 등록한다")
    void createProduct() {
        givenBot();
        ProductRequest request = new ProductRequest(1L, "운동화", null, List.of());

        sellingService.createProduct(BOT_ID, request);

        verify(productService).creation(USER_ID, request);
    }

    @Test
    @DisplayName("경매 시작: 켜 둔 봇 방송에서 봇 계정으로 경매를 연다")
    void startLiveAuction() {
        givenBot();
        givenLive();
        LiveAuctionCreateRequest request = new LiveAuctionCreateRequest(5L, 1000L, 100L, null, 600);

        sellingService.startLiveAuction(BOT_ID, request);

        verify(auctionService).createLiveAuction(USER_ID, LIVE_ID, request);
    }

    @Test
    @DisplayName("경매 시작·영상 위에 띄우기: 봇 방송이 꺼져 있으면 LIVEBOT_004")
    void notLive() {
        givenBot();
        given(liveStreamRepository.findByActiveSellerId(USER_ID)).willReturn(Optional.empty());

        assertError(() -> sellingService.startLiveAuction(BOT_ID, new LiveAuctionCreateRequest(5L, 1000L, 100L, null, 600)),
                LiveBotErrorCode.NOT_LIVE);
        assertError(() -> sellingService.featureAuction(BOT_ID, 9L), LiveBotErrorCode.NOT_LIVE);
        verify(auctionService, never()).createLiveAuction(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("영상 위에 띄우기: 봇 방송에 봇 계정으로 띄운다")
    void featureAuction() {
        givenBot();
        givenLive();

        sellingService.featureAuction(BOT_ID, 9L);

        verify(liveStreamService).featureAuction(LIVE_ID, USER_ID, 9L);
    }

    @Test
    @DisplayName("주문: 봇 계정의 판매 내역을 읽고, 봇 계정으로 발송 처리한다")
    void orders() {
        givenBot();

        sellingService.getOrders(BOT_ID, null, null, 20);
        sellingService.ship(BOT_ID, 7L, new OrderShipRequest(Courier.values()[0], "123456789"));

        verify(orderService).getOrders(USER_ID, OrderRole.SELLER, null, null, 20);
        verify(orderService).ship(USER_ID, 7L, Courier.values()[0], "123456789");
    }

    @Test
    @DisplayName("없는 봇이면 LIVEBOT_001이고 다른 서비스를 부르지 않는다")
    void botNotFound() {
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.empty());

        assertError(() -> sellingService.cancelAuction(BOT_ID, 9L), LiveBotErrorCode.NOT_FOUND);
        verify(auctionService, never()).cancelAuction(any(), any());
    }
}
