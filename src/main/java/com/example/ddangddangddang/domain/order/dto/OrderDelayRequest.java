package com.example.ddangddangddang.domain.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderDelayRequest(
        @NotBlank @Size(max = 200) String reason
) {
}
