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

    //연장 규칙(기본 마감 30초전 이내 입찰시 30초 연장,최대 횟수 없음)
    private static final int BEFORE_DEADLINE_SECONDS = 30; //마감 몇초전
    private static final int EXTENSION_SECONDS =30; //몇 초 연장


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

    private LocalDateTime canceledAt;

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

    //마감 직전 입찰로 종로 시간이 연장된 횟수
    @Column(nullable = false)
    private int extendCount;

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
        this.extendCount =0;
        this.status = AuctionStatus.READY;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    //경매 취소(status==CANCELED 전환+취소 시간 기록)
    public void cancel () {
        this.status = AuctionStatus.CANCELED;
        this.canceledAt=LocalDateTime.now();
    }

    //경매 시작. AuctionStatus를 ONGOING로 변환(라이브 중 경매 생성시,일반 경매는 시작 시간에 변환 예정)
    public void start(){
        this.status=AuctionStatus.ONGOING;
    }

    //방송에 올리기 : 이미 만든 경매를 방송 화면에 보여준다(경매 일정·상태는 그대로)
    public void showOn(LiveStream liveStream){
        this.liveStream=liveStream;
    }

    //경매 마감 : 입찰이 있을시 ENDED,없으면 FAILED
    public void close(){
        if (this.bidCount > 0){
            this.status =AuctionStatus.ENDED;
        }else {
            this.status=AuctionStatus.FAILED;
        }
    }

    //마감 직점 입찰시 종료 시간 연장(입찰 성공시 호출,연장 여부는 조건에 맞을시 연장)
    //연장시 true, 마감 직전이 아니면 false
    public boolean extendEndAtIfNeeded(LocalDateTime bidAt){
        if (bidAt.isBefore(this.endAt.minusSeconds(BEFORE_DEADLINE_SECONDS))){
            return false;
        }
        this.endAt=this.endAt.plusSeconds(EXTENSION_SECONDS);
        this.extendCount++;
        return true;
    }

}
