package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 판매자 신청 (반려된 뒤 다시 신청할 때도 같은 요청) */
public record SellerApplicationRequest(
        @NotBlank(message = "상점 이름을 입력해 주세요.")
        @Size(max = SellerApplication.STORE_NAME_MAX_LENGTH, message = "상점 이름은 30자 이하입니다.")
        String storeName,

        @NotBlank(message = "판매자 소개를 입력해 주세요.")
        @Size(max = SellerApplication.INTRODUCTION_MAX_LENGTH, message = "판매자 소개는 500자 이하입니다.")
        String introduction
) {
}
