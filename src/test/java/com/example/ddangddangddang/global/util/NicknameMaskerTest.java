package com.example.ddangddangddang.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NicknameMaskerTest {

    @Test
    @DisplayName("첫 글자와 마지막 글자만 남기고 가운데를 가린다")
    void mask() {
        assertThat(NicknameMasker.mask("땅땅이")).isEqualTo("땅*이");
        assertThat(NicknameMasker.mask("경매왕김씨")).isEqualTo("경***씨");
        assertThat(NicknameMasker.mask("cizett")).isEqualTo("c****t");
    }

    @Test
    @DisplayName("두 글자는 뒤 글자를, 한 글자는 전부 가린다")
    void shortNickname() {
        assertThat(NicknameMasker.mask("철수")).isEqualTo("철*");
        assertThat(NicknameMasker.mask("a")).isEqualTo("*");
    }

    @Test
    @DisplayName("이모지처럼 두 칸짜리 글자도 한 글자로 센다")
    void emoji() {
        assertThat(NicknameMasker.mask("😀경매😀")).isEqualTo("😀**😀");
    }

    @Test
    @DisplayName("닉네임이 없으면 알 수 없음")
    void unknown() {
        assertThat(NicknameMasker.mask(null)).isEqualTo(NicknameMasker.UNKNOWN);
        assertThat(NicknameMasker.mask(" ")).isEqualTo(NicknameMasker.UNKNOWN);
    }
}
