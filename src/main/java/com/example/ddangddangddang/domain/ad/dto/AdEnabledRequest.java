package com.example.ddangddangddang.domain.ad.dto;

import jakarta.validation.constraints.NotNull;

/** 광고 켜기·끄기 */
public record AdEnabledRequest(
        @NotNull(message = "켜기·끄기 값은 필수입니다.")
        Boolean enabled
) {
}
