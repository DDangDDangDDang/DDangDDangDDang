package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 인증받은 판매자의 상점 이름·소개 수정 (다시 심사받지 않는다) */
public record SellerStoreUpdateRequest(
        @NotBlank(message = "상점 이름을 입력해 주세요.")
        @Size(max = SellerApplication.STORE_NAME_MAX_LENGTH, message = "상점 이름은 30자 이하입니다.")
        String storeName,

        @NotBlank(message = "판매자 소개를 입력해 주세요.")
        @Size(max = SellerApplication.INTRODUCTION_MAX_LENGTH, message = "판매자 소개는 500자 이하입니다.")
        String introduction
) {
}
