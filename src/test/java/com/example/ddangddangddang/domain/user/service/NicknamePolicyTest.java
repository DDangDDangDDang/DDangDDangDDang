package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerationProperties;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("폭 없는 공백, 한글 채움 문자 같은 보이지 않는 문자가 있으면 USER_016 (금칙어 사이에 끼워 검사를 피하지 못하게)")
    void invisibleCharacters() {
        assertThatThrownBy(() -> policy.validate("금\u200B칙어"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.validate("경매\u200D왕"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        // 한글 채움 문자·빈 점자 (투명 닉네임, 금칙어 우회)
        assertThatThrownBy(() -> policy.validate("금\u3164칙어"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.validate("abc\u3164"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.validate("abc\uFFA0"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.validate("\u115Fabc"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.validate("ab\u1160c"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
        assertThatThrownBy(() -> policy.validate("\u2800abc"))
                .extracting("errorCode").isEqualTo(UserErrorCode.NICKNAME_NOT_ALLOWED);
    }

    @Test
    @DisplayName("닉네임 형식은 앞뒤 전각 공백(U+3000)도 공백으로 막는다")
    void unicodeWhitespaceFormat() {
        java.util.regex.Pattern format = java.util.regex.Pattern.compile(
                com.example.ddangddangddang.domain.auth.dto.NicknameFormat.REGEX);
        assertThat(format.matcher("abc").matches()).isTrue();
        assertThat(format.matcher("경매 왕").matches()).isTrue();
        assertThat(format.matcher("abc\u3000").matches()).isFalse();
        assertThat(format.matcher("\u3000abc").matches()).isFalse();
        assertThat(format.matcher(" abc").matches()).isFalse();
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
