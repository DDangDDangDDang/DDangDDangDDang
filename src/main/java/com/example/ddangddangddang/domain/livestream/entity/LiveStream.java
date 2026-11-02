package com.example.ddangddangddang.domain.livestream.entity;

import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Entity
@Table(
        name = "live_streams",
        // 판매자당 종료되지 않은 방송은 하나. 동시에 두 개를 만들어도 DB에서 하나만 성공한다.
        uniqueConstraints = @UniqueConstraint(name = "uk_live_stream_active_seller", columnNames = "active_seller_id"),
        indexes = {
                @Index(name = "idx_live_stream_seller_status", columnList = "seller_id, status"),
                // 홈 화면: 진행 중인 방송을 최근 시작순으로
                @Index(name = "idx_live_stream_status_started", columnList = "status, started_at"),
                // 홈 화면: 방송 예정인 방송을 시작 예정 시간순으로
                @Index(name = "idx_live_stream_status_scheduled", columnList = "status, scheduled_start_at"),
                // 방송 목록: 종료된 방송을 최근 종료순으로
                @Index(name = "idx_live_stream_status_ended", columnList = "status, ended_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveStream extends BaseTimeEntity {

    /** 영상 위에 한 번에 띄울 수 있는 경매 수 */
    public static final int MAX_FEATURED_AUCTIONS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: 회원 엔티티가 정해지면 @ManyToOne 으로 바꿈
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String title;

    // 방송 소개. 없으면 null
    @Column(length = 1000)
    private String description;

    // 채팅창 위에 고정하는 판매자 공지. 없으면 null
    @Column(length = 200)
    private String notice;

    // 방송 목록 카드에 보여 주는 해시태그. "#나이키 #운동화"처럼 공백으로 이어 저장한다. 없으면 null (LiveStreamHashtags)
    @Column(length = LiveStreamHashtags.COLUMN_LENGTH)
    private String hashtags;

    // 판매자가 영상 위에 띄운 경매. 최대 MAX_FEATURED_AUCTIONS(2)개라 두 칸에 둔다. 비어 있으면 null
    // 먼저 띄운 경매가 첫 칸이고, 첫 칸을 내리면 둘째 칸이 앞으로 온다. (시청 화면은 이 순서로 위에서부터 보여 준다)
    // 방송과 경매는 별개 도메인이라 연관관계 없이 ID만 둔다. 이 방송에 올라간 경매인지는 띄울 때 서비스에서 확인한다.
    @Column(name = "featured_auction_id")
    private Long featuredAuctionId;

    @Column(name = "second_featured_auction_id")
    private Long secondFeaturedAuctionId;

    // 판매자가 정한 방송 화면 배치 (경매 카드·선물·입찰·낙찰 안내 위치). 정하지 않았으면 null이고 화면은 기본 배치로 그린다.
    @Embedded
    private OverlayLayout overlayLayout;

    // 카메라 대신 미리 녹화한 영상을 내보내는 방송인지. 판매자가 스튜디오에서 영상 소스를 바꾸면 함께 바뀐다.
    // 시청 화면·방송 목록·구독 목록·방송 시작 알림에 "녹화 영상" 표시를 한다.
    // 이미 있는 방송과 SQL로 직접 넣는 행은 카메라 방송(false)으로 본다.
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean recorded;

    // 채팅 AI 검토 결과를 어떻게 처리할지 (ChatModerationMode). 이미 있는 방송과 SQL로 직접 넣는 행은 알림만(ALERT)으로 본다.
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ALERT'")
    @Column(name = "chat_moderation_mode", nullable = false, length = 20)
    private ChatModerationMode chatModerationMode = ChatModerationMode.ALERT;

    // LiveKit 방 이름. 외부에 노출되므로 순번 ID 대신 UUID 사용
    @Column(name = "room_name", nullable = false, unique = true, length = 64)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LiveStreamStatus status;

    // 판매자가 정한 방송 시작 예정 시간. 정하지 않았으면 null (바로 시작하는 방송)
    // 실제 시작 시간(startedAt)과는 따로 둔다. 예정보다 늦거나 일찍 시작할 수 있다.
    @Column(name = "scheduled_start_at")
    private LocalDateTime scheduledStartAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    // 종료되지 않은(대기 중·방송 중) 방송이면 sellerId, 종료되면 null. 이 컬럼에 유니크를 걸어 판매자당 하나만 허용한다.
    // (유니크 인덱스는 null을 여러 개 허용하므로 종료된 방송은 몇 개든 남을 수 있다)
    @Column(name = "active_seller_id")
    private Long activeSellerId;

    // 방송 카드 썸네일: 송출 중인 판매자 화면을 주기적으로 캡처한 이미지의 저장소 키 (live-thumbnails/{방송 ID}/...)
    // 화면 주소는 FileStorage.publicUrl로 만든다. 아직 캡처하지 않았으면 null (카드에 판매자 이름 첫 글자를 보여 준다)
    // 자주 바뀌므로 엔티티 변경 대신 LiveStreamRepository.updateThumbnailKey로 고친다. (낙관적 락 버전을 올리지 않는다)
    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    // 시작 버튼 연타, 여러 서버에서 동시에 상태를 바꾸는 경우 한 건만 성공시키기 위한 낙관적 락
    @Version
    private Long version;

    private LiveStream(Long sellerId, String title, String description, LocalDateTime scheduledStartAt) {
        this.sellerId = sellerId;
        this.title = title;
        this.description = description;
        this.scheduledStartAt = scheduledStartAt;
        this.roomName = "live-" + UUID.randomUUID();
        this.status = LiveStreamStatus.SCHEDULED;
        this.activeSellerId = sellerId;
    }

    public static LiveStream create(Long sellerId, String title) {
        return create(sellerId, title, null);
    }

    public static LiveStream create(Long sellerId, String title, String description) {
        return create(sellerId, title, description, null);
    }

    public static LiveStream create(Long sellerId, String title, String description, LocalDateTime scheduledStartAt) {
        return new LiveStream(sellerId, title, description, scheduledStartAt);
    }

    public boolean update(Long requesterId, String title, String description) {
        return update(requesterId, title, description, null);
    }

    /**
     * 방송 정보를 바꾼다. null인 값은 바꾸지 않는다. 대기 중이거나 방송 중일 때만 바꿀 수 있다.
     * description을 빈 문자열로 보내면 설명을 지운다. (서비스에서 null 대신 빈 문자열로 넘김)
     * 시작 예정 시간은 방송을 시작하기 전(대기 중)에만 바꿀 수 있다. 시작한 뒤에는 의미가 없다.
     *
     * @return 실제로 바뀐 값이 있으면 true
     */
    public boolean update(Long requesterId, String title, String description, LocalDateTime scheduledStartAt) {
        validateOwner(requesterId);
        if (status == LiveStreamStatus.ENDED) {
            throw new BusinessException(LiveStreamErrorCode.ALREADY_ENDED);
        }
        if (scheduledStartAt != null && status != LiveStreamStatus.SCHEDULED) {
            throw new BusinessException(LiveStreamErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
        }
        boolean changed = false;
        if (scheduledStartAt != null && !scheduledStartAt.equals(this.scheduledStartAt)) {
            this.scheduledStartAt = scheduledStartAt;
            changed = true;
        }
        if (title != null && !title.equals(this.title)) {
            this.title = title;
            changed = true;
        }
        if (description != null) {
            String newDescription = description.isEmpty() ? null : description;
            if (!Objects.equals(newDescription, this.description)) {
                this.description = newDescription;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * 채팅창 고정 공지를 바꾼다. 빈 문자열이면 지운다. 방송 정보 수정(update)으로 주인·종료 여부를 확인한 뒤 호출한다.
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean changeNotice(String notice) {
        String newNotice = notice.isEmpty() ? null : notice;
        if (Objects.equals(newNotice, this.notice)) {
            return false;
        }
        this.notice = newNotice;
        return true;
    }

    /** 해시태그 목록(# 없이). 없으면 빈 목록 */
    public List<String> getHashtagList() {
        return LiveStreamHashtags.fromColumn(hashtags);
    }

    /**
     * 해시태그를 바꾼다. 빈 목록이면 지운다. LiveStreamHashtags.normalize를 거친 값을 넘긴다.
     * 만들 때는 바로, 수정할 때는 방송 정보 수정(update)으로 주인·종료 여부를 확인한 뒤 호출한다.
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean changeHashtags(List<String> tags) {
        String column = LiveStreamHashtags.toColumn(tags);
        if (Objects.equals(column, this.hashtags)) {
            return false;
        }
        this.hashtags = column;
        return true;
    }

    /**
     * 녹화 영상 방송인지 바꾼다. 방송 정보 수정(update)으로 주인·종료 여부를 확인한 뒤 호출한다.
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean changeRecorded(boolean recorded) {
        if (this.recorded == recorded) {
            return false;
        }
        this.recorded = recorded;
        return true;
    }

    /**
     * 채팅 AI 검토 처리 방식을 바꾼다. 방송 정보 수정(update)으로 주인·종료 여부를 확인한 뒤 호출한다.
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean changeChatModerationMode(ChatModerationMode mode) {
        if (this.chatModerationMode == mode) {
            return false;
        }
        this.chatModerationMode = mode;
        return true;
    }

    /** 영상 위에 띄운 경매 ID (띄운 순서, 없으면 빈 목록) */
    public List<Long> getFeaturedAuctionIds() {
        return Stream.of(featuredAuctionId, secondFeaturedAuctionId).filter(Objects::nonNull).toList();
    }

    /**
     * 경매를 영상 위에 띄운다. 방송 중일 때만, 방송 주인만 띄울 수 있다.
     * 이미 띄운 경매 중 끝났거나 취소된 것(stillShowable이 false)은 먼저 내려 자리를 비운다. (시청 화면에서도 이미 보이지 않음)
     * 그래도 MAX_FEATURED_AUCTIONS개가 차 있으면 하나를 내린 뒤에 띄울 수 있다.
     *
     * @return 실제로 바뀌었으면 true (이미 띄운 경매면 false)
     */
    public boolean featureAuction(Long requesterId, Long auctionId, Predicate<Long> stillShowable) {
        validateOwner(requesterId);
        if (status == LiveStreamStatus.ENDED) {
            throw new BusinessException(LiveStreamErrorCode.ALREADY_ENDED);
        }
        if (status != LiveStreamStatus.LIVE) {
            throw new BusinessException(LiveStreamErrorCode.NOT_LIVE);
        }
        if (getFeaturedAuctionIds().contains(auctionId)) {
            return false;
        }
        List<Long> kept = getFeaturedAuctionIds().stream().filter(stillShowable).toList();
        if (kept.size() >= MAX_FEATURED_AUCTIONS) {
            throw new BusinessException(LiveStreamErrorCode.FEATURED_AUCTION_LIMIT);
        }
        setFeaturedAuctions(Stream.concat(kept.stream(), Stream.of(auctionId)).toList());
        return true;
    }

    /**
     * 영상 위에 띄운 경매를 내린다. auctionId가 null이면 모두 내린다. 종료 전이면 할 수 있고, 방송 주인만 내릴 수 있다.
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean unfeatureAuction(Long requesterId, Long auctionId) {
        validateOwner(requesterId);
        if (status == LiveStreamStatus.ENDED) {
            throw new BusinessException(LiveStreamErrorCode.ALREADY_ENDED);
        }
        List<Long> before = getFeaturedAuctionIds();
        List<Long> after = auctionId == null ? List.of() : before.stream().filter(id -> !id.equals(auctionId)).toList();
        if (after.equals(before)) {
            return false;
        }
        setFeaturedAuctions(after);
        return true;
    }

    /**
     * 방송 화면 배치를 바꾼다. 종료 전이면 방송 시작 전에도 미리 정할 수 있다. 방송 주인만 바꿀 수 있다.
     *
     * @return 실제로 바뀌었으면 true
     */
    public boolean changeOverlayLayout(Long requesterId, OverlayLayout layout) {
        validateOverlayEditable(requesterId);
        if (Objects.equals(layout, this.overlayLayout)) {
            return false;
        }
        this.overlayLayout = layout;
        return true;
    }

    /** 방송 화면 배치를 고칠 수 있는지: 방송 주인이고 종료 전이어야 한다. (이미지 박스 업로드 URL을 줄 때도 같은 조건) */
    public void validateOverlayEditable(Long requesterId) {
        validateOwner(requesterId);
        if (status == LiveStreamStatus.ENDED) {
            throw new BusinessException(LiveStreamErrorCode.ALREADY_ENDED);
        }
    }

    /** 지금 화면 배치의 박스 (정하지 않았으면 빈 목록) */
    public List<OverlayBox> overlayBoxes() {
        return overlayLayout == null ? List.of() : overlayLayout.boxesOrEmpty();
    }

    private void setFeaturedAuctions(List<Long> ids) {
        this.featuredAuctionId = ids.isEmpty() ? null : ids.get(0);
        this.secondFeaturedAuctionId = ids.size() < 2 ? null : ids.get(1);
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
        // 종료되면 판매자가 새 방송을 만들 수 있게 비운다.
        this.activeSellerId = null;
        setFeaturedAuctions(List.of());
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
