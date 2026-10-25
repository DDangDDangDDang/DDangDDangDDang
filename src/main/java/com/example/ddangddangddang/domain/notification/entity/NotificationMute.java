package com.example.ddangddangddang.domain.notification.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원이 끈 알림 묶음. 기본은 모두 켜져 있어 끈 것만 저장한다.
 * 같은 묶음을 두 번 끄지 못하도록 (user_id, category)를 유니크로 둔다.
 */
@Entity
@Table(
        name = "notification_mutes",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_mute", columnNames = {"user_id", "category"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationMute extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationCategory category;

    private NotificationMute(Long userId, NotificationCategory category) {
        this.userId = userId;
        this.category = category;
    }

    public static NotificationMute create(Long userId, NotificationCategory category) {
        return new NotificationMute(userId, category);
    }
}
