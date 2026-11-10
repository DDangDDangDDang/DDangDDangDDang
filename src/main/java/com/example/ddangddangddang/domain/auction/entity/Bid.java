package com.example.ddangddangddang.domain.auction.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성공한 입찰 기록. 금액·입찰자는 한 번 저장되면 바뀌지 않고, 상태(status)만 밀려남·낙찰에 따라 바뀐다.
 * 상태는 경매 행을 잠근 트랜잭션(입찰·즉시구매·마감) 안에서만 일괄 UPDATE로 바꾼다. (BidRepository)
 * 경매는 id로만 참조한다. 입찰 기록을 조회할 때 경매 엔티티까지 불러올 필요가 없어서다.
 */
@Getter
@Entity
@Table(
        name = "bids",
        indexes = {
                @Index(name = "idx_bids_auction_id", columnList = "auction_id, id"),
                // 입찰마다 이 경매의 최고 입찰(ACTIVE)을 OUTBID로 바꾼다 (BidRepository.changeStatus).
                // 이 인덱스가 없으면 경매의 입찰 행을 전부 훑고 잠가, 입찰이 쌓일수록 느려지고 동시 입찰끼리 오래 기다린다
                @Index(name = "idx_bids_auction_status", columnList = "auction_id, status")
        }
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

    // 상태 컬럼 도입 전 입찰은 비어 있다(null). db/backfill/bids_status.sql로 채운다.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BidStatus status;

    private Bid(Long auctionId, Long bidderId, long amount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.status = BidStatus.ACTIVE;
    }

    public static Bid create(Long auctionId, Long bidderId, long amount) {
        return new Bid(auctionId, bidderId, amount);
    }
}
