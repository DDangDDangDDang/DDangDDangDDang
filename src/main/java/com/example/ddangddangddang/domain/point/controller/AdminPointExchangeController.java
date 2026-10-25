package com.example.ddangddangddang.domain.point.controller;

import com.example.ddangddangddang.domain.point.dto.AdminPointExchangeResponse;
import com.example.ddangddangddang.domain.point.dto.PointExchangeRejectRequest;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import com.example.ddangddangddang.domain.point.service.AdminPointExchangeService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 수동 지급. /api/v1/admin/** 은 SecurityConfig에서 ADMIN 권한만 통과한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/point-exchanges")
public class AdminPointExchangeController {

    private final AdminPointExchangeService adminPointExchangeService;

    /** 환전 신청 목록 (먼저 신청한 순). 지급할 건만 보려면 status=PENDING */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<AdminPointExchangeResponse>>> getExchanges(
            @RequestParam(required = false) PointExchangeType status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(adminPointExchangeService.getExchanges(status, cursor, size)));
    }

    /** 송금 시작 (PENDING → PROCESSING). 은행 이체 전에 먼저 누른다. 이후 사용자는 취소할 수 없다 */
    @PostMapping("/{exchangeId}/start")
    public ResponseEntity<ApiResponse<AdminPointExchangeResponse>> startPayout(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long exchangeId) {
        return ResponseEntity.ok(ApiResponse.ok(adminPointExchangeService.startPayout(authUser.id(), exchangeId)));
    }

    /** 지급 완료 (PROCESSING → COMPLETE). 이체가 끝난 걸 확인하고 누른다 */
    @PostMapping("/{exchangeId}/complete")
    public ResponseEntity<ApiResponse<AdminPointExchangeResponse>> completePayout(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long exchangeId) {
        return ResponseEntity.ok(ApiResponse.ok(adminPointExchangeService.completePayout(authUser.id(), exchangeId)));
    }

    /** 반려 (PENDING·PROCESSING → FAILED). 포인트는 즉시 환불된다 */
    @PostMapping("/{exchangeId}/reject")
    public ResponseEntity<ApiResponse<AdminPointExchangeResponse>> reject(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long exchangeId,
            @Valid @RequestBody PointExchangeRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                adminPointExchangeService.reject(authUser.id(), exchangeId, request.reason())));
    }
}
