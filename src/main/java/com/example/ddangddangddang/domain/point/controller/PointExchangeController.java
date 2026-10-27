package com.example.ddangddangddang.domain.point.controller;

import com.example.ddangddangddang.domain.point.dto.PointExchangeRequest;
import com.example.ddangddangddang.domain.point.dto.PointExchangeResponse;
import com.example.ddangddangddang.domain.point.service.PointExchangeService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/points/exchanges")
public class PointExchangeController {

    private final PointExchangeService pointExchangeService;

    /** 환전 신청. 포인트는 즉시 차감되고, 송금은 백그라운드에서 처리된다 (응답 status: PENDING) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<PointExchangeResponse>> requestExchange(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PointExchangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(pointExchangeService.requestExchange(authUser.id(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<PointExchangeResponse>>> getExchanges(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(pointExchangeService.getExchanges(authUser.id(), cursor, size)));
    }

    @GetMapping("/{exchangeId}")
    public ResponseEntity<ApiResponse<PointExchangeResponse>> getExchange(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long exchangeId) {
        return ResponseEntity.ok(ApiResponse.ok(pointExchangeService.getExchange(authUser.id(), exchangeId)));
    }

    /** 송금 시작 전(PENDING)에만 취소 가능. 포인트는 즉시 환불된다 */
    @PostMapping("/{exchangeId}/cancel")
    public ResponseEntity<ApiResponse<PointExchangeResponse>> cancelExchange(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long exchangeId) {
        return ResponseEntity.ok(ApiResponse.ok(pointExchangeService.cancelExchange(authUser.id(), exchangeId)));
    }
}
