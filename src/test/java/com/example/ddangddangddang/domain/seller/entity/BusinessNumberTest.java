package com.example.ddangddangddang.domain.seller.entity;

import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessNumberTest {

    @Test
    @DisplayName("검증 숫자가 맞는 10자리면 하이픈·공백을 빼고 숫자만 남긴다")
    void normalize() {
        assertThat(BusinessNumber.normalize("220-81-62517")).isEqualTo("2208162517");
        assertThat(BusinessNumber.normalize(" 1248100998 ")).isEqualTo("1248100998");
    }

    @Test
    @DisplayName("비어 있으면 개인 판매자(null)")
    void blank() {
        assertThat(BusinessNumber.normalize(null)).isNull();
        assertThat(BusinessNumber.normalize("  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"220-81-62518", "123456789", "12345678901", "abc-de-fghij"})
    @DisplayName("검증 숫자가 틀리거나 10자리가 아니면 SELLER_007")
    void invalid(String input) {
        assertThatThrownBy(() -> BusinessNumber.normalize(input))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(SellerErrorCode.INVALID_BUSINESS_NUMBER);
    }
}
