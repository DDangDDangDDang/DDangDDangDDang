package com.example.ddangddangddang.domain.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRequest(
        @NotBlank @Size(max = 20) String name,
        Integer sortOrder
) {
}
