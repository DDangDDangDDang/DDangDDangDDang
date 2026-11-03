package com.example.ddangddangddang.domain.hammer.dto;

import com.example.ddangddangddang.domain.hammer.entity.SanctionType;
import jakarta.validation.constraints.NotNull;

public record SanctionReleaseRequest(@NotNull Long userId, @NotNull SanctionType type) {
}
