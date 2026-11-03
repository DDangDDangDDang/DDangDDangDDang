package com.example.ddangddangddang.domain.hammer.controller;

import com.example.ddangddangddang.domain.hammer.dto.AdminSanctionResponse;
import com.example.ddangddangddang.domain.hammer.dto.SanctionReleaseRequest;
import com.example.ddangddangddang.domain.hammer.service.SanctionService;
import com.example.ddangddangddang.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 정지 검토. /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 접근할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/sanctions")
public class AdminSanctionController {

    private final SanctionService sanctionService;

    /** 최근 90일 본인 책임 취소가 4회 이상인 회원 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminSanctionResponse>>> getReviewTargets() {
        return ResponseEntity.ok(ApiResponse.ok(sanctionService.reviewTargets()));
    }

    /** 정지를 푼다. 지금까지의 취소는 그 정지 종류에서 다시 세지 않는다 */
    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> release(@Valid @RequestBody SanctionReleaseRequest request) {
        sanctionService.release(request.userId(), request.type());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
