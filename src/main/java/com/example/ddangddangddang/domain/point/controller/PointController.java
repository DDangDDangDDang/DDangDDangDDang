package com.example.ddangddangddang.domain.point.controller;

import com.example.ddangddangddang.domain.point.dto.PointHistoryResponse;
import com.example.ddangddangddang.domain.point.dto.PointWalletResponse;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/points")
public class PointController {

    private final PointService pointService;

    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<PointWalletResponse>> getWallet(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(pointService.getWallet(authUser.id())));
    }

    @GetMapping("/histories")
    public ResponseEntity<ApiResponse<CursorResponse<PointHistoryResponse>>> getHistories(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) PointHistoryType type,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(pointService.getHistories(authUser.id(), type, cursor, size)));
    }
}