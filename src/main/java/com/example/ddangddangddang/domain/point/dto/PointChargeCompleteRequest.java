package com.example.ddangddangddang.domain.point.dto;

import jakarta.validation.constraints.NotBlank;

public record PointChargeCompleteRequest(
        @NotBlank(message = "결제 ID가 없습니다.")
        String paymentId
) { }
