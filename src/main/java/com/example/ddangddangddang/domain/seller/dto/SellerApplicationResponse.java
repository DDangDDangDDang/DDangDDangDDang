package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;

import java.time.LocalDateTime;

/**
 * 판매자 신청. 관리자 목록에서는 신청한 회원의 닉네임·이메일도 함께 보여 준다. (내 신청 조회에서는 null)
 * rejectReason: 반려됐을 때만, reviewedAt: 승인·반려한 시각
 */
public record SellerApplicationResponse(
        Long id,
        Long userId,
        String nickname,
        String email,
        String storeName,
        String introduction,
        SellerApplicationStatus status,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
    public static SellerApplicationResponse from(SellerApplication application) {
        return of(application, null, null);
    }

    public static SellerApplicationResponse of(SellerApplication application, String nickname, String email) {
        return new SellerApplicationResponse(application.getId(), application.getUserId(), nickname, email,
                application.getStoreName(), application.getIntroduction(), application.getStatus(),
                application.getRejectReason(), application.getCreatedAt(), application.getReviewedAt());
    }
}
