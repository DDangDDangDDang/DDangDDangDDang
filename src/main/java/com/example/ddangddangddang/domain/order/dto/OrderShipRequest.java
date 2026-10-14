package com.example.ddangddangddang.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderShipRequest(

        @NotBlank(message = "송장번호는 필수입니다.")
        @Size(max = 50, message = "송장번호는 50자 이하여야 합니다.")
        String trackingNo
) {
}