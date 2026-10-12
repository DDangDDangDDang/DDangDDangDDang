package com.example.ddangddangddang.domain.livestream.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livekit")
public record LiveKitProperties(
        String apiUrl,
        String wsUrl,
        String apiKey,
        String apiSecret
) {
}
