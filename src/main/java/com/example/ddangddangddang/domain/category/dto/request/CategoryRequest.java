package com.example.ddangddangddang.domain.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        Long parentId,
        @NotBlank @Size(max = 20) String name,
        Integer sortOrder
) {

}
