package com.example.ddangddangddang.domain.order.controller;

import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.service.OrderService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 주문 확인: 구매자가 문제를 신고했거나 송장을 확인하지 못해 자동 구매 확정이 멈춘 주문.
 * 관리자가 확인한 뒤 판매자 정산(confirm) 또는 구매자 전액 환불(refund)로 마무리한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderReviewController {

    private final OrderService orderService;

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<CursorResponse<OrderResponse>>> getReviewOrders(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getReviewOrders(cursor, size)));
    }

    @PostMapping("/{orderId}/review/confirm")
    public ResponseEntity<ApiResponse<OrderResponse>> confirm(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.resolveReviewAsConfirmed(orderId)));
    }

    @PostMapping("/{orderId}/review/refund")
    public ResponseEntity<ApiResponse<OrderResponse>> refund(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.resolveReviewAsRefunded(orderId)));
    }
}
