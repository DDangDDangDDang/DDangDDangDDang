package com.example.ddangddangddang.domain.livebot.service;

import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionResponse;
import com.example.ddangddangddang.domain.auction.service.AuctionService;
import com.example.ddangddangddang.domain.livebot.entity.LiveBot;
import com.example.ddangddangddang.domain.livebot.exception.LiveBotErrorCode;
import com.example.ddangddangddang.domain.livebot.repository.LiveBotRepository;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.dto.OrderShipRequest;
import com.example.ddangddangddang.domain.order.dto.OrderSummaryResponse;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.service.OrderService;
import com.example.ddangddangddang.domain.product.dto.request.ProductImageUploadRequest;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductImageUploadResponse;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.product.service.ProductService;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 라이브 봇으로 실제 물건 팔기 (관리자). 운영자가 봇 계정 이름으로 상품을 올리고, 봇 방송에서 경매를 열고, 들어온 주문을 발송한다.
 *
 * 상품·경매·주문 규칙은 사람 판매자와 똑같다. 각 도메인 서비스를 봇 계정 ID로 부를 뿐 규칙을 새로 만들지 않는다.
 * 그래서 시청자의 입찰·즉시구매·포인트·주문·발송 기한·정산도 보통 경매와 같고, 정산 금액은 봇 계정 포인트로 들어간다.
 * 트랜잭션은 각 도메인 서비스가 연다. (한 요청에 서비스 하나만 부른다)
 */
@Service
@RequiredArgsConstructor
public class LiveBotSellingService {

    private final LiveBotRepository liveBotRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final LiveStreamService liveStreamService;
    private final ProductService productService;
    private final AuctionService auctionService;
    private final OrderService orderService;

    // ---------- 상품 ----------

    /** 봇의 상품 (삭제한 상품 제외, 최근 등록순) */
    public Page<ProductResponse> getProducts(Long botId, Pageable pageable) {
        return productService.myProducts(botUserId(botId), null, null, pageable);
    }

    /** 상품 이미지 업로드 URL. 사람 판매자와 같은 규칙(JPG·PNG·WEBP·GIF, 10MB) */
    public ProductImageUploadResponse createProductImageUpload(Long botId, ProductImageUploadRequest request) {
        return productService.createImageUpload(botUserId(botId), request);
    }

    /** 상품 등록 (판매자는 봇 계정) */
    public ProductResponse createProduct(Long botId, ProductRequest request) {
        return productService.creationAsBot(botUserId(botId), request);
    }

    // ---------- 경매 ----------

    /** 봇의 시작 전·진행 중 경매 */
    public List<AuctionResponse> getOpenAuctions(Long botId) {
        return auctionService.myOpenAuctions(botUserId(botId));
    }

    /** 지금 켜 둔 봇 방송에서 경매를 바로 시작한다. 방송이 꺼져 있으면 LIVEBOT_004. 방송이 끝나도 경매는 정한 시간까지 이어진다 */
    public AuctionResponse startLiveAuction(Long botId, LiveAuctionCreateRequest request) {
        LiveBot bot = findById(botId);
        return auctionService.createLiveAuctionAsBot(bot.getUserId(), liveStreamId(bot), request);
    }

    /** 경매를 봇 방송 영상 위에 띄운다. (최대 2개, 사람 방송과 같은 규칙) */
    public LiveStreamResponse featureAuction(Long botId, Long auctionId) {
        LiveBot bot = findById(botId);
        return liveStreamService.featureAuction(liveStreamId(bot), bot.getUserId(), auctionId);
    }

    /** 영상 위에 띄운 경매를 내린다 */
    public LiveStreamResponse unfeatureAuction(Long botId, Long auctionId) {
        LiveBot bot = findById(botId);
        return liveStreamService.unfeatureAuction(liveStreamId(bot), bot.getUserId(), auctionId);
    }

    /** 경매 취소. 입찰이 하나도 없을 때만 된다 (사람 판매자와 같은 규칙) */
    public void cancelAuction(Long botId, Long auctionId) {
        auctionService.cancelAuction(botUserId(botId), auctionId);
    }

    // ---------- 주문 ----------

    /** 봇이 판 주문 (판매 내역) */
    public CursorResponse<OrderSummaryResponse> getOrders(Long botId, OrderStatus status, Long cursor, int size) {
        return orderService.getOrders(botUserId(botId), OrderRole.SELLER, status, cursor, size);
    }

    /** 주문 상세 (배송지 포함). 봇이 판 주문만 볼 수 있다 */
    public OrderResponse getOrder(Long botId, Long orderId) {
        return orderService.getOrder(botUserId(botId), orderId);
    }

    /** 발송 처리 (택배사·송장번호) */
    public OrderResponse ship(Long botId, Long orderId, OrderShipRequest request) {
        return orderService.ship(botUserId(botId), orderId, request.courier(), request.trackingNo());
    }

    /** 발송 지연 신청. 주문당 한 번, 구매자에게 메신저로 안내한다 */
    public OrderResponse requestDelay(Long botId, Long orderId, String reason) {
        return orderService.requestDelay(botUserId(botId), orderId, reason);
    }

    private Long botUserId(Long botId) {
        return findById(botId).getUserId();
    }

    // 봇이 지금 켜 둔 방송. 봇 방송은 만들자마자 시작하므로 종료되지 않은 방송은 곧 방송 중인 방송이다.
    private Long liveStreamId(LiveBot bot) {
        return liveStreamRepository.findByActiveSellerId(bot.getUserId())
                .filter(stream -> stream.getStatus() == LiveStreamStatus.LIVE)
                .map(LiveStream::getId)
                .orElseThrow(() -> new BusinessException(LiveBotErrorCode.NOT_LIVE));
    }

    private LiveBot findById(Long botId) {
        return liveBotRepository.findById(botId).orElseThrow(() -> new BusinessException(LiveBotErrorCode.NOT_FOUND));
    }
}
