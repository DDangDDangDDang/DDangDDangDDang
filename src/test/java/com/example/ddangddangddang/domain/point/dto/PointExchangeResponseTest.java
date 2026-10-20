package com.example.ddangddangddang.domain.point.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PointExchangeResponseTest {

    @Test
    @DisplayName("계좌번호는 앞 3자리와 뒤 3자리만 보인다")
    void mask() {
        assertThat(PointExchangeResponse.mask("1101234567")).isEqualTo("110****567");
        assertThat(PointExchangeResponse.mask("110123456789")).isEqualTo("110******789");
    }
}
