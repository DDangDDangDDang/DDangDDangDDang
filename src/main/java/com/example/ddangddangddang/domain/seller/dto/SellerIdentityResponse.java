package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.seller.entity.SellerIdentity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 내 본인인증 결과. 인증한 본인에게만 보여 준다. (CI는 내보내지 않는다) */
public record SellerIdentityResponse(
        String realName,
        LocalDate birthDate,
        LocalDateTime verifiedAt
) {
    public static SellerIdentityResponse from(SellerIdentity identity) {
        return new SellerIdentityResponse(identity.getRealName(), identity.getBirthDate(), identity.getVerifiedAt());
    }
}
