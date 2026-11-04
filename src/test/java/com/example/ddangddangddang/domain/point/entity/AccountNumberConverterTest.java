package com.example.ddangddangddang.domain.point.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountNumberConverterTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private final AccountNumberConverter converter = new AccountNumberConverter(KEY);

    @Test
    @DisplayName("계좌번호를 암호화해 저장하고 다시 읽으면 원래 값이 된다")
    void roundTrip() {
        String stored = converter.convertToDatabaseColumn("110123456789");

        assertThat(stored).startsWith(AccountNumberConverter.PREFIX).doesNotContain("110123456789");
        assertThat(stored.length()).isLessThanOrEqualTo(255);
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo("110123456789");
    }

    @Test
    @DisplayName("같은 계좌번호도 저장할 때마다 다른 값이 된다")
    void randomIv() {
        assertThat(converter.convertToDatabaseColumn("110123456789"))
                .isNotEqualTo(converter.convertToDatabaseColumn("110123456789"));
    }

    @Test
    @DisplayName("암호화를 넣기 전에 저장된 평문 계좌번호는 그대로 읽는다")
    void legacyPlaintext() {
        assertThat(converter.convertToEntityAttribute("110123456789")).isEqualTo("110123456789");
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("다른 키로 암호화된 값은 읽지 못하고 예외를 낸다 (잘못된 값을 조용히 돌려주지 않는다)")
    void wrongKey() {
        String stored = converter.convertToDatabaseColumn("110123456789");
        AccountNumberConverter other = new AccountNumberConverter(
                Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes()));

        assertThatThrownBy(() -> other.convertToEntityAttribute(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키가 32바이트가 아니면 서버를 띄우지 않는다")
    void invalidKeyLength() {
        assertThatThrownBy(() -> new AccountNumberConverter(Base64.getEncoder().encodeToString("short".getBytes())))
                .isInstanceOf(IllegalStateException.class);
    }
}
