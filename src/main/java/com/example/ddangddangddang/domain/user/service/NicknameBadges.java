package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원이 장착한 닉네임 꾸미기와 지금 닉네임. 라이브 채팅이 메시지를 보낼 때마다 읽어 Redis에 캐시한다. (CacheConfig.NICKNAME_BADGE, 10분)
 * 장착이나 닉네임을 바꾸면 캐시를 지운다. (UserBadgeService, UserService) Redis가 안 되면 DB에서 읽는다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NicknameBadges {

    private final UserRepository userRepository;

    @Cacheable(cacheNames = CacheConfig.NICKNAME_BADGE, key = "#userId")
    public EquippedBadge equipped(Long userId) {
        return userRepository.findById(userId)
                .map(user -> new EquippedBadge(user.getEquippedBadge(), user.getEquippedTitle(), user.getEquippedColor(), user.getNickname()))
                .orElse(EquippedBadge.NONE);
    }
}
