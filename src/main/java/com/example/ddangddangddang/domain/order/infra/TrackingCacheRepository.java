package com.example.ddangddangddang.domain.order.infra;

import com.example.ddangddangddang.domain.order.entity.Courier;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/**
 * tracking:{택배사}:{송장번호} → 배송 조회 결과(JSON).
 * 같은 송장을 새로고침할 때마다 외부 API를 부르지 않도록 잠깐 저장한다. (무료 사용량·응답 속도)
 */
@Repository
@RequiredArgsConstructor
public class TrackingCacheRepository {

    private static final String KEY_PREFIX = "tracking:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<TrackingInfo> find(Courier courier, String trackingNo) {
        String json = redisTemplate.opsForValue().get(key(courier, trackingNo));
        return Optional.ofNullable(json).map(value -> objectMapper.readValue(value, TrackingInfo.class));
    }

    public void save(Courier courier, String trackingNo, TrackingInfo info, Duration ttl) {
        redisTemplate.opsForValue().set(key(courier, trackingNo), objectMapper.writeValueAsString(info), ttl);
    }

    private String key(Courier courier, String trackingNo) {
        return KEY_PREFIX + courier.name() + ":" + trackingNo;
    }
}