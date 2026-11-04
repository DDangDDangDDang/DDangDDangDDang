package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerationProperties;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NicknamePolicyTest {

    private final NicknamePolicy policy =
            new NicknamePolicy(new ChatContentFilter(new ChatModerationProperties(List.of("금칙어"), null)));

    @Test
    @DisplayName("보통 닉네임은 통과한다")
    void allowed() {
        assertThatCode(() -> policy.validate("경매왕")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("abc123")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("금칙어·휴대폰 번호처럼 채팅에서 막는 내용이면 USER_016")
    void rejected() {
        assertThatThrownBy(() -> policy.validate("금칙어왕"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.validate("01012345678"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
    }
}
