package com.example.ddangddangddang.domain.user.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원이 가진 닉네임 뱃지 하나. 영구로 가지며, (회원, 뱃지)는 하나만 있다. */
@Getter
@Entity
@Table(name = "user_badges",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_badge", columnNames = {"user_id", "badge"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBadge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // MySQL ENUM이 아니라 문자열로 둔다. (뱃지를 더할 때 ddl-auto가 ENUM 정의를 고치지 않음)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private NicknameBadge badge;

    private UserBadge(Long userId, NicknameBadge badge) {
        this.userId = userId;
        this.badge = badge;
    }

    public static UserBadge of(Long userId, NicknameBadge badge) {
        return new UserBadge(userId, badge);
    }
}
