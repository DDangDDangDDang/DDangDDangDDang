package com.example.ddangddangddang.domain.directmessage.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DirectMessagePreviewTest {

    @Test
    @DisplayName("줄바꿈과 연속 공백은 공백 하나로 바꾼다")
    void oneLine() {
        assertThat(DirectMessageService.preview("첫 줄\n\n둘째   줄")).isEqualTo("첫 줄 둘째 줄");
    }

    @Test
    @DisplayName("100자를 넘으면 100자까지만 남기고, 이모지를 반으로 자르지 않는다")
    void cutByCodePoint() {
        String emojis = "😀".repeat(120);

        String preview = DirectMessageService.preview(emojis);

        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(100);
        assertThat(preview).isEqualTo("😀".repeat(100));
    }

    @Test
    @DisplayName("100자 이하면 그대로 둔다")
    void shortMessage() {
        assertThat(DirectMessageService.preview("짧은 메시지")).isEqualTo("짧은 메시지");
    }
}
