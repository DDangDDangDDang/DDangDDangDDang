package com.example.ddangddangddang.domain.auction.entity;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name ="auctions")
public class Auction extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id",nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "live_stream_id")
    private LiveStream liveStream;

    @Column(nullable = false)
    private Long startPrice;

    @Column(nullable = false)
    private Long bidUnit;

    private Long buyNowPrice;

    @Column(nullable = false)
    private Long currentPrice;

    @Column(nullable = false)
    private int bidCount;


    private Long highestBidderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    private AuctionStatus status;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    @Version
    private Long version;

    //경매 생성(현재가==시작가,입찰수 =0,status=READY로 시작)
    public Auction(Product product, LiveStream liveStream, Long startPrice, Long bidUnit,
                   Long buyNowPrice, LocalDateTime startAt, LocalDateTime endAt) {
        this.product = product;
        this.liveStream = liveStream;
        this.startPrice = startPrice;
        this.bidUnit = bidUnit;
        this.buyNowPrice = buyNowPrice;
        this.currentPrice = startPrice;
        this.bidCount = 0;
        this.status = AuctionStatus.READY;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    //경매 취소(status==CANCELED 전환)
    public void cancel () {
        this.status = AuctionStatus.CANCELED;
    }

}
