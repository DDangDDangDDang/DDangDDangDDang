package com.example.ddangddangddang.domain.address.dto.request;

import jakarta.validation.constraints.*;

//배송지 저장,수정 요청
public record UserAddressRequest(
        @NotBlank(message = "배송지 이름은 필수 입니다")
        @Size(max = 20, message = "배송지 이름은 20자 이하여야 합니다")
        String addressName,

        @NotBlank(message = "받는 사람 이름은 필수 입니다.")
        @Size(max = 20, message = "받는 사람 이름은 20자 이하여야 합니다")
        String receiverName,

        // 택배 기사가 연락할 번호라 집 전화(02 등) 허용. 하이폰 없어 숫자만
        @NotBlank(message = "받는 사람 연락처는 필수 입니다.")
        @Pattern(regexp = "^0\\d{8,10}$",message = " 연락처는 하이폰 없이 숫자만 입력해주세요")
        String receiverPhone,

        @NotBlank(message = "주소는 필수 입니다.")
        @Size(max = 200, message = "주소는 200자 이하여야 합니다")
        String address,

        @Size(max = 100, message = "배송 요청사항은 100자 이하 여야 합니다.")
        String deliveryMemo


) {
}
