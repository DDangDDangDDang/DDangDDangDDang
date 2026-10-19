package com.example.ddangddangddang.domain.point.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointChargeRequest {

    @NotNull(message = "충전 금액이 없습니다.")
    @Positive(message = "충전 금액은 0보다 커야합니다.")
    private Long amount;
}
