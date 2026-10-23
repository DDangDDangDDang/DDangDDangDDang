package com.example.ddangddangddang.domain.notification.dto;

/** 알림 받는 방식. pushWhenVisible: 사이트를 보고 있을 때도 브라우저 알림을 띄울지 (기본 false) */
public record NotificationPreferenceResponse(boolean pushWhenVisible) {
}
