package com.example.ddangddangddang.global.config;

import com.example.ddangddangddang.domain.donation.policy.DonationPolicy;
import com.example.ddangddangddang.domain.category.dto.response.CategoryResponse;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    //캐시 이름
    public static final String CATEGORIES ="categories";
    // 도네이션 포인트 정책 (DonationPolicies.current). 선물·적립마다 읽어 캐시하고, 관리자가 바꾸면 지운다.
    public static final String DONATION_POLICY = "donationPolicy";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory){
        //카테고리 목록(List<CategoryResponse>)을 JSON으로 저장 및 복원
        JsonMapper jsonMapper = JsonMapper.builder().build();
        JavaType categoryListType = jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, CategoryResponse.class);

        RedisCacheConfiguration categoryConfig =RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) //삭제를 놓쳐도 10분 뒤 자동 새로 만들기
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper,categoryListType)
                ));
        RedisCacheConfiguration donationPolicyConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) //삭제를 놓쳐도 10분 뒤 자동 새로 만들기
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper, jsonMapper.getTypeFactory().constructType(DonationPolicy.class))
                ));
        // 캐시 쓰기·삭제를 끝까지 기다린다. (immediateWrites)
        // Spring Data Redis 4는 Lettuce를 쓰면 기본으로 비동기로 지워서, 카테고리·정책을 바꾸자마자 다시 조회하면 지우기 전의 예전 값이 나왔다.
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites());
        return RedisCacheManager.builder(cacheWriter)
                .withCacheConfiguration(CATEGORIES, categoryConfig)
                .withCacheConfiguration(DONATION_POLICY, donationPolicyConfig)
                .transactionAware() //DB 커밋이 끝난 뒤에 캐시 삭제
                .build();


    }

    //Redis가 잠까 안될시 : 에러를 내지 않고 로그만 남긴 뒤 DB에서 조회
    @Override
    public CacheErrorHandler errorHandler(){
        return new LoggingCacheErrorHandler();
    }


}
