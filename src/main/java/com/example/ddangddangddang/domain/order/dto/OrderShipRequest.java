package com.example.ddangddangddang.domain.order.dto;

import com.example.ddangddangddang.domain.order.entity.Courier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OrderShipRequest(

        @NotNull(message = "택배사는 필수입니다.")
        Courier courier,

        /* 하이픈을 넣고 빼는 것만으로 같은 송장을 다시 등록하지 못하도록 숫자만 받는다. */
        @NotBlank(message = "송장번호는 필수입니다.")
        @Pattern(regexp = "^\\d{8,20}$", message = "송장번호는 하이픈 없이 숫자 8~20자리로 입력해 주세요.")
        String trackingNo
) {
}