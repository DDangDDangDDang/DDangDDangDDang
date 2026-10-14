package com.example.ddangddangddang.domain.livestream.entity;

import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "live_streams",
        indexes = @Index(name = "idx_live_stream_seller_status", columnList = "seller_id, status")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveStream extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: 회원 엔티티가 정해지면 @ManyToOne 으로 바꿈
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String title;

    // LiveKit 방 이름. 외부에 노출되므로 순번 ID 대신 UUID 사용
    @Column(name = "room_name", nullable = false, unique = true, length = 64)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LiveStreamStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    // 시작 버튼 연타, 여러 서버에서 동시에 상태를 바꾸는 경우 한 건만 성공시키기 위한 낙관적 락
    @Version
    private Long version;

    private LiveStream(Long sellerId, String title) {
        this.sellerId = sellerId;
        this.title = title;
        this.roomName = "live-" + UUID.randomUUID();
        this.status = LiveStreamStatus.SCHEDULED;
    }

    public static LiveStream create(Long sellerId, String title) {
        return new LiveStream(sellerId, title);
    }

    public void start(Long requesterId) {
        validateOwner(requesterId);
        if (status != LiveStreamStatus.SCHEDULED) {
            throw new BusinessException(LiveStreamErrorCode.INVALID_STATUS_TO_START);
        }
        this.status = LiveStreamStatus.LIVE;
        this.startedAt = LocalDateTime.now();
    }

    public void end(Long requesterId) {
        validateOwner(requesterId);
        this.status = LiveStreamStatus.ENDED;
        this.endedAt = LocalDateTime.now();
    }

    public void validateJoinable() {
        if (status != LiveStreamStatus.LIVE) {
            throw new BusinessException(LiveStreamErrorCode.NOT_LIVE);
        }
    }

    /**
     * 송출 중인 방송에 판매자가 다시 접속할 수 있는지 확인한다. (새로고침, 송출 토큰 만료, 연결 끊김)
     * 방송 시작은 LIVE 상태에서 다시 할 수 없으므로(ALREADY_LIVE) 송출 토큰만 다시 발급할 때 쓴다.
     */
    public void validatePublishable(Long requesterId) {
        validateOwner(requesterId);
        if (status != LiveStreamStatus.LIVE) {
            throw new BusinessException(LiveStreamErrorCode.NOT_LIVE);
        }
    }

    public boolean isOwner(Long memberId) {
        return sellerId.equals(memberId);
    }

    private void validateOwner(Long requesterId) {
        if (!isOwner(requesterId)) {
            throw new BusinessException(LiveStreamErrorCode.NOT_OWNER);
        }
    }


}
