package com.example.ddangddangddang.domain.auction.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 경매 조회수를 Redis에 잠깐 모아 둔다. 스케줄러가 1분마다 DB로 옮긴다.

 * 저장하는 것 두가지
 * - auction:views:pending                         경매별로 아직 DB에 옮기지 않은 조회수
 * - auction:views:viewer:{경매ID}:{사람}    "회원 2가 5번 경매를 봤음" 표시. 30분 뒤 사라진다.
 *   표시가 남아 있는 동안 또 볼시 조회수를 올리지 않는다(새로고침으로 조작 방지)
 */
@Repository
@RequiredArgsConstructor
public class AuctionViewRepository {

    static final String VIEWS_KEY = "auction:views:pending";
    static final String VIEWER_KEY_PREFIX = "auction:views:viewer:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 처음 본 사람이면 조회수를 1씩 올린다.
     * 같은 사람이 30분 안에 다시 보면 올리지 않는다. (새로고침 반복 방지)
     */
    public boolean increaseIfFirstView(Long auctionId, String viewer, Duration ttl){
        String viewerKey = VIEWER_KEY_PREFIX + auctionId + ":" + viewer;
        //표시가 없을 때만 새로 만든다. 새로 만들었으면 처음 본것이다.
        boolean firstView = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(viewerKey, "1",ttl));
        if (firstView){
            redisTemplate.opsForZSet().incrementScore(VIEWS_KEY, String.valueOf(auctionId), 1);
        }
        return firstView;
    }


    // 아직 DB에 옮기지 않은 조회수를 "경매 ID -> 조회수"로 가져온다.
    public Map<Long, Long> allViewCounts(){
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(VIEWS_KEY, 0, -1);
        if (tuples == null){
            return Map.of();
        }
        HashMap<Long, Long> result = new HashMap<>();
        for (TypedTuple<String> tuple : tuples){
            if (tuple.getValue() != null && tuple.getScore() != null){
                result.put(Long.parseLong(tuple.getValue()),tuple.getScore().longValue());
            }
        }
        return result;

    }

    // DB에 옮긴 숫자만큼만 뺀다. 옮기는 동안 새로 본 사람 수는 남아서 다음에 옮겨진다. 0이 되면 지운다.
    public void subtract(Map<Long,Long> movedCounts){
        movedCounts.forEach((auctionId,count)-> redisTemplate.opsForZSet().incrementScore(VIEWS_KEY, String.valueOf(auctionId), -count));
        redisTemplate.opsForZSet().removeRangeByScore(VIEWS_KEY,Double.NEGATIVE_INFINITY,0);
    }
}
