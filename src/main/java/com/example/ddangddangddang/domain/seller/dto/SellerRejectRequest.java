package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 판매자 신청 반려. 사유는 신청한 회원에게 보인다. */
public record SellerRejectRequest(
        @NotBlank(message = "반려 사유를 입력해 주세요.")
        @Size(max = SellerApplication.REJECT_REASON_MAX_LENGTH, message = "반려 사유는 200자 이하입니다.")
        String reason
) {
}
