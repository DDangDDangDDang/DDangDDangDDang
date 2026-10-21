package com.example.ddangddangddang.domain.notification.repository;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 내 알림 최신순. cursor는 이전 페이지 마지막 알림 ID
    @Query("select n from Notification n where n.userId = :userId and (:cursor is null or n.id < :cursor) order by n.id desc")
    List<Notification> findByCursor(@Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int readAll(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
