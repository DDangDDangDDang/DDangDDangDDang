package com.example.ddangddangddang.domain.point.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 환전 송금 워커와 복구 스케줄러를 위한 스케줄링 활성화.
 * 공용 Application 클래스를 건드리지 않으려고 포인트 도메인 안에 둔다.
 * 테스트에서는 point.exchange.worker.enabled=false 로 끈다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "point.exchange.worker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PointSchedulingConfig {
}
