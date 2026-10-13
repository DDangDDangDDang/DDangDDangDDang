package com.example.ddangddangddang.domain.category.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        Long parentId,
        @NotNull @Size(max = 20) String name,
        Integer sortOrder
) {

}
