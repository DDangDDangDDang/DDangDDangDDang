package com.example.ddangddangddang.domain.livestream.entity;

import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiveStreamHashtagsTest {

    @Test
    @DisplayName("앞의 #과 공백을 빼고, 빈 값은 버리고, 대소문자만 다른 중복은 처음 것만 남긴다")
    void normalize() {
        assertThat(LiveStreamHashtags.normalize(Arrays.asList(" #나이키 ", "##운동화", "", null, "Nike", "nike", "#")))
                .containsExactly("나이키", "운동화", "Nike");
        assertThat(LiveStreamHashtags.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("5개까지 넣을 수 있다 (중복을 뺀 뒤 센다)")
    void maxCount() {
        assertThat(LiveStreamHashtags.normalize(List.of("a", "b", "c", "d", "e", "A"))).hasSize(5);
        assertThatThrownBy(() -> LiveStreamHashtags.normalize(List.of("a", "b", "c", "d", "e", "f")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해시태그는 5개까지 넣을 수 있습니다.")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_HASHTAG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"띄어 쓴 태그", "나이키#운동화", "한정판!", "가나다라마바사아자차카타파하가나다라마바사", "%", "a,b"})
    @DisplayName("공백·#·특수문자가 있거나 20자를 넘으면 LIVESTREAM_015")
    void invalid(String tag) {
        assertThatThrownBy(() -> LiveStreamHashtags.normalize(List.of(tag)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(LiveStreamErrorCode.INVALID_HASHTAG);
    }

    @Test
    @DisplayName("20자, 숫자·밑줄은 넣을 수 있다")
    void valid() {
        assertThat(LiveStreamHashtags.normalize(List.of("가나다라마바사아자차카타파하가나다라마바", "2026_신상"))).hasSize(2);
    }

    @Test
    @DisplayName("저장 값은 \"#태그 #태그\"이고, 다시 읽으면 # 없는 목록이다. 없으면 null")
    void column() {
        String column = LiveStreamHashtags.toColumn(List.of("나이키", "운동화"));

        assertThat(column).isEqualTo("#나이키 #운동화");
        assertThat(LiveStreamHashtags.fromColumn(column)).containsExactly("나이키", "운동화");
        assertThat(LiveStreamHashtags.toColumn(List.of())).isNull();
        assertThat(LiveStreamHashtags.fromColumn(null)).isEmpty();
    }
}
