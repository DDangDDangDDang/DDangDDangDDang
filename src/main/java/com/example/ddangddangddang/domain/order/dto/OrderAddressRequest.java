package com.example.ddangddangddang.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrderAddressRequest(

        @NotBlank(message = "받는 사람 이름은 필수입니다.")
        @Size(max = 20, message = "받는 사람 이름은 20자 이하여야 합니다.")
        String receiverName,

        /* 택배 기사가 연락할 번호라 집 전화(02 등)도 허용한다. 하이픈 없이 숫자만 받는다. */
        @NotBlank(message = "받는 사람 연락처는 필수입니다.")
        @Pattern(regexp = "^0\\d{8,10}$", message = "연락처는 하이픈 없이 숫자만 입력해 주세요. (예: 01012345678)")
        String receiverPhone,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다.")
        String address,

        @Size(max = 100, message = "배송 요청사항은 100자 이하여야 합니다.")
        String deliveryMemo
) {
}