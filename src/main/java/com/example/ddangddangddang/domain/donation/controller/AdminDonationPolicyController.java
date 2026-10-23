package com.example.ddangddangddang.domain.donation.controller;

import com.example.ddangddangddang.domain.donation.policy.DonationPolicies;
import com.example.ddangddangddang.domain.donation.policy.DonationPolicyRequest;
import com.example.ddangddangddang.domain.donation.policy.DonationPolicyResponse;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 도네이션 포인트 정책 (적립 비율·한도·유효기간·선물 규칙·파워 판매자). /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 접근할 수 있다.
 * 바꾸면 배포·재시작 없이 바로 모든 서버에 적용된다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/donation-policy")
public class AdminDonationPolicyController {

    private final DonationPolicies donationPolicies;

    /** 지금 정책 */
    @GetMapping
    public ResponseEntity<ApiResponse<DonationPolicyResponse>> getCurrent() {
        return ResponseEntity.ok(ApiResponse.ok(donationPolicies.currentForAdmin()));
    }

    /** 바꾼 기록 (최근부터) */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<CursorResponse<DonationPolicyResponse>>> getHistory(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(donationPolicies.history(cursor, size)));
    }

    /** 정책 바꾸기. 모든 값을 보낸다. */
    @PutMapping
    public ResponseEntity<ApiResponse<DonationPolicyResponse>> change(@AuthenticationPrincipal AuthUser authUser,
                                                                      @Valid @RequestBody DonationPolicyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(donationPolicies.change(authUser.id(), request)));
    }
}
