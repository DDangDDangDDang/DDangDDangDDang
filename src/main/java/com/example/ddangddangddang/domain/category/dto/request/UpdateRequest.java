package com.example.ddangddangddang.domain.category.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRequest(
        @NotNull @Size(max = 20) String name,
        Integer sortOrder
) {
}
