package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.dto.MyBadgesResponse;
import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserBadge;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserBadgeRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.config.CacheConfig;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 닉네임 뱃지 보유·장착. 뱃지는 마일리지 상점에서 산다. (ShopService → grant)
 * 장착을 바꾸면 라이브 채팅이 쓰는 장착 캐시를 커밋 뒤 지운다. (NicknameBadges)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBadgeService {

    private final UserRepository userRepository;
    private final UserBadgeRepository userBadgeRepository;

    public MyBadgesResponse getMine(Long userId) {
        return response(findUser(userId));
    }

    public Set<NicknameBadge> owned(Long userId) {
        return userBadgeRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(UserBadge::getBadge)
                .collect(Collectors.toSet());
    }

    /**
     * 뱃지를 준다(상점 구매)와 함께 바로 장착한다. 마일리지 결제와 같은 트랜잭션에서 부른다. (ShopService)
     * 이미 가진 뱃지면 USER_014. 같은 뱃지를 동시에 두 번 사도 (회원, 뱃지) 유니크가 막는다.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.NICKNAME_BADGE, key = "#userId")
    public MyBadgesResponse grant(Long userId, NicknameBadge badge) {
        User user = findUser(userId);
        if (userBadgeRepository.existsByUserIdAndBadge(userId, badge)) {
            throw new BusinessException(UserErrorCode.BADGE_ALREADY_OWNED);
        }
        try {
            userBadgeRepository.saveAndFlush(UserBadge.of(userId, badge));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(UserErrorCode.BADGE_ALREADY_OWNED);
        }
        user.equipBadge(badge);
        return response(user);
    }

    /** 가진 뱃지 중 하나를 장착한다. null이면 뺀다. 갖지 않은 뱃지면 USER_015 */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.NICKNAME_BADGE, key = "#userId")
    public MyBadgesResponse equip(Long userId, NicknameBadge badge) {
        User user = findUser(userId);
        if (badge != null && !userBadgeRepository.existsByUserIdAndBadge(userId, badge)) {
            throw new BusinessException(UserErrorCode.BADGE_NOT_OWNED);
        }
        user.equipBadge(badge);
        return response(user);
    }

    private MyBadgesResponse response(User user) {
        List<NicknameBadge> owned = userBadgeRepository.findByUserIdOrderByIdAsc(user.getId()).stream()
                .map(UserBadge::getBadge)
                .toList();
        return new MyBadgesResponse(owned, user.getEquippedBadge());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
