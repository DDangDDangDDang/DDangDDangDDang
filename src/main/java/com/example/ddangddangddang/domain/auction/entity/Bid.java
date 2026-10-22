package com.example.ddangddangddang.domain.auction.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성공한 입찰 기록. 한 번 저장되면 바뀌지 않는다.
 * 경매는 id로만 참조한다. 입찰 기록을 조회할 때 경매 엔티티까지 불러올 필요가 없어서다.
 */
@Getter
@Entity
@Table(
        name = "bids",
        indexes = @Index(name = "idx_bids_auction_id", columnList = "auction_id, id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long auctionId;

    @Column(nullable = false)
    private Long bidderId;

    @Column(nullable = false)
    private long amount;

    private Bid(Long auctionId, Long bidderId, long amount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
    }

    public static Bid create(Long auctionId, Long bidderId, long amount) {
        return new Bid(auctionId, bidderId, amount);
    }
}
