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
        String introduction,

        // 사업자 판매자만: 사업자등록번호 10자리 (하이픈 있어도 됨). 개인 판매자는 비워 둔다.
        @Size(max = 20, message = "사업자등록번호 10자리를 다시 확인해 주세요.")
        String businessNumber
) {
    public SellerApplicationRequest(String storeName, String introduction) {
        this(storeName, introduction, null);
    }
}
