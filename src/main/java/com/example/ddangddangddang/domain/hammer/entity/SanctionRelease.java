package com.example.ddangddangddang.domain.hammer.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관리자가 정지를 푼 기록. 푼 시각까지의 취소는 그 정지 종류에서 다시 세지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "sanction_releases",
        indexes = @Index(name = "idx_sanction_releases_user_type", columnList = "userId, type, releasedAt"))
public class SanctionRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    // MySQL ENUM이 아니라 문자열로 둔다. (값을 더해도 컬럼을 바꾸지 않아도 된다)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private SanctionType type;

    @Column(nullable = false)
    private LocalDateTime releasedAt;

    private SanctionRelease(Long userId, SanctionType type, LocalDateTime releasedAt) {
        this.userId = userId;
        this.type = type;
        this.releasedAt = releasedAt;
    }

    public static SanctionRelease of(Long userId, SanctionType type, LocalDateTime releasedAt) {
        return new SanctionRelease(userId, type, releasedAt);
    }
}
