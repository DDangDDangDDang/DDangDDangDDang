package com.example.ddangddangddang.domain.notification.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VapidKeysTest {

    @Test
    @DisplayName("키가 비어 있으면 로컬은 새로 만들고, 키를 꼭 받아야 하는 운영 설정이면 서버가 뜨지 않는다")
    void blankKeys() {
        assertThat(new VapidKeys(new WebPushProperties(true, "", "", null, null, null, false)).publicKey()).isNotBlank();
        assertThatThrownBy(() -> new VapidKeys(new WebPushProperties(true, "", " ", null, null, null, true)))
                .isInstanceOf(IllegalStateException.class);
    }
}
