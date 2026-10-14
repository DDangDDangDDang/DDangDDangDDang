package com.example.ddangddangddang.domain.order.controller;

import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.dto.OrderShipRequest;
import com.example.ddangddangddang.domain.order.dto.OrderSummaryResponse;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.service.OrderService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /** role=BUYER(기본값)면 구매 내역, role=SELLER면 판매 내역 */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<OrderSummaryResponse>>> getOrders(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "BUYER") OrderRole role,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrders(authUser.id(), role, status, cursor, size)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getOrder(authUser.id(), orderId)));
    }

    @PatchMapping("/{orderId}/ship")
    public ResponseEntity<ApiResponse<OrderResponse>> ship(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long orderId,
            @Valid @RequestBody OrderShipRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.ship(authUser.id(), orderId, request.trackingNo())));
    }

    @PatchMapping("/{orderId}/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmPurchase(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.confirmPurchase(authUser.id(), orderId)));
    }
}