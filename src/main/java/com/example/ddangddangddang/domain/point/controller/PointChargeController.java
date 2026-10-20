package com.example.ddangddangddang.domain.point.controller;

import com.example.ddangddangddang.domain.point.dto.PointChargeCompleteRequest;
import com.example.ddangddangddang.domain.point.dto.PointChargeCompleteResponse;
import com.example.ddangddangddang.domain.point.dto.PointChargeRequest;
import com.example.ddangddangddang.domain.point.dto.PointChargeResponse;
import com.example.ddangddangddang.domain.point.service.PointChargeService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/points/charges")
public class PointChargeController {

    private final PointChargeService pointChargeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<PointChargeResponse>> createChargeRequest(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PointChargeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(pointChargeService.createChargeRequest(authUser.id(), request)));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<PointChargeCompleteResponse>> completeCharge(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PointChargeCompleteRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(pointChargeService.completeCharge(authUser.id(), request.paymentId())));
    }
}