package com.example.ddangddangddang.domain.notification.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationContentTest {

    @Test
    @DisplayName("방송 시작 알림은 녹화 영상 방송이면 제목에 녹화 영상이라고 알린다")
    void liveStartedRecorded() {
        NotificationContent live = NotificationContent.of(Notification.liveStarted(5L, 1L, "판매자", 3L, "오늘의 방송"));
        NotificationContent recorded = NotificationContent.of(Notification.liveStarted(5L, 1L, "판매자", 3L, "오늘의 방송", true));

        assertThat(live.title()).isEqualTo("판매자님이 방송을 시작했어요");
        assertThat(recorded.title()).isEqualTo("판매자님이 녹화 영상 방송을 시작했어요");
        assertThat(recorded.body()).isEqualTo("오늘의 방송");
        assertThat(recorded.link()).isEqualTo("/lives/3");
    }
}
