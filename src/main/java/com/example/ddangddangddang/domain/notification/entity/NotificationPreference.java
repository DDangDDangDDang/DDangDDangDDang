package com.example.ddangddangddang.domain.notification.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 알림 받는 방식. 기본값과 다르게 바꾼 회원만 저장한다.
 * pushWhenVisible: 사이트를 보고 있을 때도 브라우저 알림(웹 푸시)을 띄운다. 기본 false (보고 있으면 화면 안 팝업만)
 */
@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_preference_user", columnNames = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "push_when_visible", nullable = false)
    private boolean pushWhenVisible;

    private NotificationPreference(Long userId, boolean pushWhenVisible) {
        this.userId = userId;
        this.pushWhenVisible = pushWhenVisible;
    }

    public static NotificationPreference create(Long userId, boolean pushWhenVisible) {
        return new NotificationPreference(userId, pushWhenVisible);
    }

    public void changePushWhenVisible(boolean pushWhenVisible) {
        this.pushWhenVisible = pushWhenVisible;
    }
}
