package com.example.ddangddangddang.domain.seller.controller;

import com.example.ddangddangddang.domain.seller.dto.SellerApplicationResponse;
import com.example.ddangddangddang.domain.seller.dto.SellerRejectRequest;
import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;
import com.example.ddangddangddang.domain.seller.service.SellerApplicationService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 판매자 신청 심사. /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 접근할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/seller-applications")
public class AdminSellerApplicationController {

    private final SellerApplicationService applicationService;

    /** 신청 목록. status를 주면 그 상태만 (심사할 신청은 PENDING) */
    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<SellerApplicationResponse>>> getApplications(
            @RequestParam(required = false) SellerApplicationStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.getApplications(status, cursor, size)));
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> approve(@PathVariable Long applicationId) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.approve(applicationId)));
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<ApiResponse<SellerApplicationResponse>> reject(
            @PathVariable Long applicationId,
            @Valid @RequestBody SellerRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(applicationService.reject(applicationId, request.reason())));
    }
}
