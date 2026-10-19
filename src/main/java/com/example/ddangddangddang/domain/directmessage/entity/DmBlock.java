package com.example.ddangddangddang.domain.directmessage.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 1:1 채팅 차단. blocker가 blocked를 차단하면 두 사람 모두 서로에게 메시지를 보낼 수 없다.
 * 대화방과 이전 메시지는 그대로 남아, 차단을 풀면 이어서 대화할 수 있다.
 */
@Entity
@Table(
        name = "dm_blocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_dm_block", columnNames = {"blocker_id", "blocked_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmBlock extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    private DmBlock(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
    }

    public static DmBlock create(Long blockerId, Long blockedId) {
        return new DmBlock(blockerId, blockedId);
    }
}
