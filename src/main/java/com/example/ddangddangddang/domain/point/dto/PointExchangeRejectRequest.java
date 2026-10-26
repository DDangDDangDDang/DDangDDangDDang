package com.example.ddangddangddang.domain.point.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 반려 사유. 사용자에게 그대로 보이므로 이해할 수 있게 적는다. 예: "예금주가 일치하지 않습니다" */
public record PointExchangeRejectRequest(
        @NotBlank @Size(max = 200) String reason
) {
}
