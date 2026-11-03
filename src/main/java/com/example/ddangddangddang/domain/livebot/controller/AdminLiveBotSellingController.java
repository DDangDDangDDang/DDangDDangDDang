package com.example.ddangddangddang.domain.livebot.controller;

import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionResponse;
import com.example.ddangddangddang.domain.livebot.service.LiveBotSellingService;
import com.example.ddangddangddang.domain.livestream.dto.FeaturedAuctionRequest;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.order.dto.OrderDelayRequest;
import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.dto.OrderShipRequest;
import com.example.ddangddangddang.domain.order.dto.OrderSummaryResponse;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.product.dto.request.ProductImageUploadRequest;
import com.example.ddangddangddang.domain.product.dto.request.ProductRequest;
import com.example.ddangddangddang.domain.product.dto.response.ProductImageUploadResponse;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자가 라이브 봇 이름으로 실제 물건을 판다: 상품 등록 → 봇 방송에서 경매 시작·영상 위에 띄우기 → 주문 발송.
 * 규칙은 사람 판매자의 API와 같다. /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 통과한다
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/live-bots/{botId}")
public class AdminLiveBotSellingController {

    private final LiveBotSellingService sellingService;

    /** 봇 상품 목록 (최근 등록순) */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getProducts(
            @PathVariable Long botId,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.getProducts(botId, pageable)));
    }

    /** 상품 이미지 업로드 URL */
    @PostMapping("/products/image-uploads")
    public ResponseEntity<ApiResponse<ProductImageUploadResponse>> createProductImageUpload(
            @PathVariable Long botId, @Valid @RequestBody ProductImageUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.createProductImageUpload(botId, request)));
    }

    /** 상품 등록 */
    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@PathVariable Long botId, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(sellingService.createProduct(botId, request)));
    }

    /** 시작 전·진행 중 경매 */
    @GetMapping("/auctions")
    public ResponseEntity<ApiResponse<List<AuctionResponse>>> getOpenAuctions(@PathVariable Long botId) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.getOpenAuctions(botId)));
    }

    /** 켜 둔 봇 방송에서 경매 바로 시작 */
    @PostMapping("/auctions")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AuctionResponse>> startLiveAuction(
            @PathVariable Long botId, @Valid @RequestBody LiveAuctionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(sellingService.startLiveAuction(botId, request)));
    }

    /** 경매 취소 (입찰이 없을 때만) */
    @PostMapping("/auctions/{auctionId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> cancelAuction(@PathVariable Long botId, @PathVariable Long auctionId) {
        sellingService.cancelAuction(botId, auctionId);
        return ResponseEntity.noContent().build();
    }

    /** 경매를 봇 방송 영상 위에 띄우기 */
    @PutMapping("/featured-auctions")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> featureAuction(
            @PathVariable Long botId, @Valid @RequestBody FeaturedAuctionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.featureAuction(botId, request.auctionId())));
    }

    /** 영상 위에 띄운 경매 내리기 */
    @DeleteMapping("/featured-auctions/{auctionId}")
    public ResponseEntity<ApiResponse<LiveStreamResponse>> unfeatureAuction(@PathVariable Long botId, @PathVariable Long auctionId) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.unfeatureAuction(botId, auctionId)));
    }

    /** 판매 내역 */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<CursorResponse<OrderSummaryResponse>>> getOrders(
            @PathVariable Long botId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.getOrders(botId, status, cursor, size)));
    }

    /** 주문 상세 (배송지 포함) */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable Long botId, @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.getOrder(botId, orderId)));
    }

    /** 발송 처리 */
    @PatchMapping("/orders/{orderId}/ship")
    public ResponseEntity<ApiResponse<OrderResponse>> ship(
            @PathVariable Long botId, @PathVariable Long orderId, @Valid @RequestBody OrderShipRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.ship(botId, orderId, request)));
    }

    /** 발송 지연 신청 */
    @PostMapping("/orders/{orderId}/delay-request")
    public ResponseEntity<ApiResponse<OrderResponse>> requestDelay(
            @PathVariable Long botId, @PathVariable Long orderId, @Valid @RequestBody OrderDelayRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(sellingService.requestDelay(botId, orderId, request.reason())));
    }
}
