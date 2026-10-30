package com.example.ddangddangddang.global.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * @Scheduled 작업 전용 스레드 풀.
 *
 * 지정하지 않으면 스레드 1개를 모든 작업이 나눠 쓰거나(스프링 기본값), WebSocket 브로커의 스케줄러를 빌려 쓴다.
 * 그러면 환전 송금(외부 호출)이나 새벽 정리 작업이 오래 걸릴 때 1초마다 도는 경매 시작·마감이 그만큼 밀린다.
 * 작업 수(현재 8개)만큼 스레드를 두어, 한 작업이 느려도 다른 작업은 제때 돈다.
 *
 * 스케줄러를 빈으로 등록하지 않는다. TaskScheduler 빈이 둘이 되면 브로커 스케줄러를 타입으로 찾는 곳이 헷갈린다.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer, DisposableBean {

    private final ThreadPoolTaskScheduler scheduler;

    public SchedulingConfig(@Value("${app.scheduling.pool-size:8}") int poolSize) {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduled-");
        // 배포 중 종료될 때 진행 중인 마감·송금 작업이 끝날 때까지 잠깐 기다린다.
        // 크론 작업의 다음 실행(예: 내일 새벽 4시)은 지연 작업으로 걸려 있으므로 버린다. 버리지 않으면 그것까지 기다리다 시간 초과된다
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(scheduler);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
    }
}
