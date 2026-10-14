package com.example.ddangddangddang.domain.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRequest(
        Long categoryId,
        @Size(max = 50) String name,
        String description
) {
}
