package com.example.ddangddangddang.domain.seller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 화면에서 마친 본인인증의 ID (포트원 본인인증 창에 넘긴 identityVerificationId) */
public record SellerIdentityRequest(
        @NotBlank(message = "본인인증 정보가 없습니다. 다시 인증해 주세요.")
        @Size(max = 100)
        String identityVerificationId
) {
}
