package com.example.ddangddangddang.domain.auction.entity;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
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

    // ===== 입찰 · 즉시구매  =====

    /** 다음 입찰이 넘어야 하는 최소 금액. 첫 입찰은 시작가부터, 이후는 현재가 + 입찰 단위 */
    public long minimumBidAmount() {
        return this.bidCount == 0 ? this.startPrice : this.currentPrice + this.bidUnit;
    }

    /**
     * 입찰이 유효한지 검증한다. 실제 반영은 AuctionRepository.placeBid(조건부 UPDATE)가 한다.
     * 검증과 반영 사이에 다른 입찰이 끼어들면 UPDATE의 version 조건에서 걸러진다.
     */
    public void validateBid(Long bidderId, long amount, LocalDateTime now) {
        validateOpen(now);
        if (isSeller(bidderId)) {
            throw new BusinessException(AuctionErrorCode.SELLER_CANNOT_BID);
        }
        if (bidderId.equals(this.highestBidderId)) {
            throw new BusinessException(AuctionErrorCode.ALREADY_HIGHEST_BIDDER);
        }
        if (amount < minimumBidAmount()) {
            throw new BusinessException(AuctionErrorCode.BID_TOO_LOW);
        }
        if (this.buyNowPrice != null && amount >= this.buyNowPrice) {
            throw new BusinessException(AuctionErrorCode.BID_REACHES_BUY_NOW);
        }
    }

    /**
     * 지금 입찰하면 바뀔 종료 시각. 연장 규칙은 extendEndAtIfNeeded와 같다 (마감 30초 전 이내면 30초 연장).
     * 엔티티를 바꾸지 않고 값만 계산한다. 조건부 UPDATE에 이 값을 넣어 원자적으로 반영하기 위해서다.
     */
    public LocalDateTime endAtAfterBid(LocalDateTime bidAt) {
        if (bidAt.isBefore(this.endAt.minusSeconds(BEFORE_DEADLINE_SECONDS))) {
            return this.endAt;
        }
        return this.endAt.plusSeconds(EXTENSION_SECONDS);
    }

    /** 즉시구매 가능 여부 검증 */
    public void validateBuyNow(Long buyerId, LocalDateTime now) {
        validateOpen(now);
        if (this.buyNowPrice == null) {
            throw new BusinessException(AuctionErrorCode.BUY_NOW_NOT_AVAILABLE);
        }
        if (isSeller(buyerId)) {
            throw new BusinessException(AuctionErrorCode.SELLER_CANNOT_BID);
        }
    }

    /** 즉시구매로 종료. 구매자가 낙찰자가 되고 즉시구매가가 최종가가 된다 */
    public void buyNow(Long buyerId, LocalDateTime now) {
        this.highestBidderId = buyerId;
        this.currentPrice = this.buyNowPrice;
        this.status = AuctionStatus.ENDED;
        this.endAt = now;
    }

    public boolean isSeller(Long userId) {
        return this.product.getSeller().getId().equals(userId);
    }

    private void validateOpen(LocalDateTime now) {
        if (this.status != AuctionStatus.ONGOING) {
            throw new BusinessException(AuctionErrorCode.AUCTION_NOT_ONGOING);
        }
        if (!now.isBefore(this.endAt)) {
            throw new BusinessException(AuctionErrorCode.AUCTION_TIME_OVER);
        }
    }
}
