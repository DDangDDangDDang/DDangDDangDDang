package com.example.ddangddangddang.global.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpEmailSenderTest {

    @Test
    @DisplayName("로그에 남기는 이메일은 첫 글자와 도메인만 보이고 나머지는 가린다")
    void maskEmail() {
        assertThat(SmtpEmailSender.maskEmail("user@example.com")).isEqualTo("u***@example.com");
        assertThat(SmtpEmailSender.maskEmail("@example.com")).isEqualTo("****");
        assertThat(SmtpEmailSender.maskEmail("abc")).isEqualTo("****");
        assertThat(SmtpEmailSender.maskEmail(null)).isEqualTo("****");
    }
}