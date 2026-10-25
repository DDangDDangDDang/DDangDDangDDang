package com.example.ddangddangddang.domain.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingRequest(@NotNull Boolean enabled) {
}
