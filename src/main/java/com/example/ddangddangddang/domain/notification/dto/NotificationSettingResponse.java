package com.example.ddangddangddang.domain.notification.dto;

import com.example.ddangddangddang.domain.notification.entity.NotificationCategory;

/** 알림 설정 한 줄. label·description은 화면에 그대로 보여 준다. enabled가 false면 그 묶음의 알림을 받지 않는다. */
public record NotificationSettingResponse(NotificationCategory category, String label, String description, boolean enabled) {

    public static NotificationSettingResponse of(NotificationCategory category, boolean enabled) {
        return new NotificationSettingResponse(category, category.getLabel(), category.getDescription(), enabled);
    }
}
