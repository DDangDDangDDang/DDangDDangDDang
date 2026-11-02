package com.example.ddangddangddang.domain.user.repository;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.entity.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserBadgeRepository extends JpaRepository<UserBadge, Long> {

    boolean existsByUserIdAndBadge(Long userId, NicknameBadge badge);

    List<UserBadge> findByUserIdOrderByIdAsc(Long userId);
}
