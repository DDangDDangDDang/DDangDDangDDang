package com.example.ddangddangddang.domain.point.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 금액 범위(1,000 ~ 1,000,000)는 도메인 규칙이라 PointExchange.create()에서 검증한다.
 */
public record PointExchangeRequest(
        @NotNull(message = "환전 금액이 없습니다.")
        Long amount,

        @NotBlank(message = "은행 코드가 없습니다.")
        @Pattern(regexp = "^\\d{3}$", message = "은행 코드는 숫자 3자리입니다.")
        String bankCode,

        @NotBlank(message = "계좌번호가 없습니다.")
        @Pattern(regexp = "^[0-9-]{10,20}$", message = "계좌번호 형식이 올바르지 않습니다.")
        String accountNumber,

        @NotBlank(message = "예금주가 없습니다.")
        @Size(max = 30, message = "예금주는 30자 이하입니다.")
        String accountHolder
) {
}
