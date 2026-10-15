package com.example.ddangddangddang.domain.point.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
        String apiUrl,
        String apiSecret
) {
}
