package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserId(Long userId);

    /** 사이트를 보고 있을 때도 브라우저 알림을 받기로 한 회원 (웹 푸시를 보낼 때 함께 담는다) */
    @Query("select p.userId from NotificationPreference p where p.userId in :userIds and p.pushWhenVisible = true")
    List<Long> findPushWhenVisibleUserIds(@Param("userIds") Collection<Long> userIds);
}
