package com.example.ddangddangddang.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * 마감 임박 알림을 보낸 경매. 경매마다 한 번만 보낸다.
 * (마감 직전 입찰로 종료 시각이 늘어나도 다시 보내지 않는다. 서버가 여러 대여도 기본키 중복으로 한 서버만 보낸다)
 */
@Entity
@Table(name = "auction_closing_notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionClosingNotice implements Persistable<Long> {

    @Id
    @Column(name = "auction_id")
    private Long auctionId;

    @Column(name = "notified_at", nullable = false)
    private LocalDateTime notifiedAt;

    public AuctionClosingNotice(Long auctionId, LocalDateTime notifiedAt) {
        this.auctionId = auctionId;
        this.notifiedAt = notifiedAt;
    }

    @Override
    public Long getId() {
        return auctionId;
    }

    /** 항상 새로 넣는다. (이미 있으면 조회 후 병합하지 않고 키 중복으로 실패하게) */
    @Override
    public boolean isNew() {
        return true;
    }
}
