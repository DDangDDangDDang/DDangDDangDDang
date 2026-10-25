package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.notification.entity.NotificationCategory;
import com.example.ddangddangddang.domain.notification.entity.NotificationMute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NotificationMuteRepository extends JpaRepository<NotificationMute, Long> {

    List<NotificationMute> findByUserId(Long userId);

    /** 알림을 받을 회원들이 끈 묶음 (보내기 전에 걸러낸다) */
    List<NotificationMute> findByUserIdIn(Collection<Long> userIds);

    boolean existsByUserIdAndCategory(Long userId, NotificationCategory category);

    @Modifying
    @Query("delete from NotificationMute m where m.userId = :userId and m.category = :category")
    int unmute(@Param("userId") Long userId, @Param("category") NotificationCategory category);
}
