package com.example.ddangddangddang.domain.hammer.controller;

import com.example.ddangddangddang.domain.hammer.dto.SanctionResponse;
import com.example.ddangddangddang.domain.hammer.service.SanctionService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 내 정지 상태. 본인 책임 취소가 반복되면 입찰 또는 경매 등록이 잠시 정지된다.
 */
@RestController
@RequiredArgsConstructor
public class SanctionController {

    private final SanctionService sanctionService;

    /** 지금 걸려 있는 정지 (없으면 빈 목록) */
    @GetMapping("/api/v1/users/me/sanctions")
    public ResponseEntity<ApiResponse<List<SanctionResponse>>> getMySanctions(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(sanctionService.mySanctions(authUser.id())));
    }
}
