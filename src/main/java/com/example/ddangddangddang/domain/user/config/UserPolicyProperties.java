package com.example.ddangddangddang.domain.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원 정보 변경 정책. 값이 없으면 기본값으로 동작한다.
 *
 * @param nicknameChangeInterval 닉네임을 바꾼 뒤 다시 바꿀 수 있을 때까지의 기간. 기본 30일.
 *                               닉네임을 자주 바꿔 다른 사람인 척하거나 신고·차단을 피하는 것을 막는다
 */
@ConfigurationProperties(prefix = "app.user")
public record UserPolicyProperties(Duration nicknameChangeInterval) {

    public UserPolicyProperties {
        if (nicknameChangeInterval == null) {
            nicknameChangeInterval = Duration.ofDays(30);
        }
    }
}
