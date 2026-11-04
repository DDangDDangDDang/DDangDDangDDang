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
import org.hibernate.annotations.ColumnDefault;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name ="auctions",
indexes = {
        // 스케줄러 : 시작시 경매 찾기(status =READY AND start_at<=현재시간)
        @Index(name = "idx_auctions_status_start_at", columnList = "status, start_at"),
        // 스케줄러 : 마감할 경매 찾기(status =ONGOING AND end_at<=현재시간)
        @Index(name = "idx_auctions_status_end_at",columnList = "status, end_at")
})
public class Auction extends BaseTimeEntity {

    //연장 규칙: 마감 30초 전 이내에 입찰하면 남은 시간이 다시 30초가 되게 마감을 미룬다 (입찰 시각 + 30초, 최대 횟수 없음)
    //예) 10초 남았을 때 입찰 → 마감이 20초 늦춰져 30초 남음. 남은 시간이 30초를 넘게 늘어나지는 않는다
    private static final int BEFORE_DEADLINE_SECONDS = 30; //마감 몇초전
    private static final int EXTENSION_SECONDS =30; //입찰 후 남겨 둘 시간

    //마감 재시도 규칙: 실패할 때마다 다음 시도까지 1초, 2초, 4초 ... 최대 5분을 기다리고, 이 횟수만큼 실패하면 멈춘다
    public static final int MAX_CLOSE_FAILURES = 10;

    //금액 상한(원 = 포인트): 시작가·입찰 단위·즉시구매가·입찰 금액. 계산이 long 범위를 넘지 않게 하고 실수 입력을 막는다
    public static final long MAX_PRICE = 100_000_000L;
    //일반 경매(시작·마감 시각을 정하는 경매) 진행 시간과 시작 시각 범위. 방송 중 경매는 LiveAuctionCreateRequest(30초~3일)
    public static final Duration MIN_DURATION = Duration.ofMinutes(1);
    public static final Duration MAX_DURATION = Duration.ofDays(14);
    public static final Duration MAX_START_AHEAD = Duration.ofDays(30);
    private static final long MAX_CLOSE_RETRY_DELAY_SECONDS = 300;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id",nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "live_stream_id")
    private LiveStream liveStream;

    //시작가·최소 입찰 단위. 둘 다 null이면 입찰 없이 즉시구매로만 파는 경매다 (isBuyNowOnly)
    //컬럼을 NULL 허용으로 바꾼 뒤에 생긴 규칙이라, 기존 DB는 db/backfill/auctions_buy_now_only.sql을 실행해야 한다
    private Long startPrice;

    private Long bidUnit;

    private Long buyNowPrice;

    @Column(nullable = false)
    private Long currentPrice;

    @Column(nullable = false)
    private int bidCount;

    //조회수. 스케줄러가 Redis 조회수를 주기적으로 옮겨 적는다 (AuctionViewRepository)
    //엔티티 저장(입찰 등) 때는 이 칸을 쓰지 않는다. 읽어 둔 옛 값으로 스케줄러가 적은 값을 덮어쓰지 않게 하려고
    @ColumnDefault("0")
    @Column(nullable = false,updatable = false)
    private long viewCount = 0;

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

    //마감(정산 포함)에 실패한 횟수. MAX_CLOSE_FAILURES에 닿으면 스케줄러가 더 시도하지 않는다 (AuctionCloseRetryService)
    @Column(nullable = false)
    private int closeFailCount;

    //마감에 실패했을 때 다음으로 시도할 시각. 실패한 적 없으면 null
    private LocalDateTime nextCloseAttemptAt;

    //경매 생성(현재가==시작가,입찰수 =0,status=READY로 시작)
    public Auction(Product product, LiveStream liveStream, Long startPrice, Long bidUnit,
                   Long buyNowPrice, LocalDateTime startAt, LocalDateTime endAt) {
        this.product = product;
        this.liveStream = liveStream;
        this.startPrice = startPrice;
        this.bidUnit = bidUnit;
        this.buyNowPrice = buyNowPrice;
        //즉시구매 전용은 현재가를 즉시구매가로 둔다. 목록의 가격 검색·정렬이 현재가 기준이라 그대로 동작한다
        this.currentPrice = startPrice != null ? startPrice : buyNowPrice;
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
            this.product.sold();
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
        LocalDateTime newEndAt = endAtAfterBid(bidAt);
        if (!newEndAt.isAfter(this.endAt)) {
            return false;
        }
        this.endAt = newEndAt;
        this.extendCount++;
        return true;
    }

    // ===== 입찰 · 즉시구매  =====

    /** 입찰 없이 즉시구매로만 파는 경매인지. 시작가·입찰 단위 없이 즉시구매가만 있다 (입찰·마감 연장 없음) */
    public boolean isBuyNowOnly() {
        return this.startPrice == null;
    }

    /** 다음 입찰이 넘어야 하는 최소 금액. 첫 입찰은 시작가부터, 이후는 현재가 + 입찰 단위 */
    public long minimumBidAmount() {
        requireBidding();
        if (this.bidCount == 0) {
            return this.startPrice;
        }
        // 상한 도입 전에 만든 경매의 큰 입찰 단위로 넘치면(음수가 되면) 어떤 입찰도 받지 않는다
        try {
            return Math.addExact(this.currentPrice, this.bidUnit);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 입찰이 유효한지 검증한다. 실제 반영은 AuctionRepository.placeBid(조건부 UPDATE)가 한다.
     * 검증과 반영 사이에 다른 입찰이 끼어들면 UPDATE의 version 조건에서 걸러진다.
     */
    public void validateBid(Long bidderId, long amount, LocalDateTime now) {
        validateOpen(now);
        requireBidding();
        if (isSeller(bidderId)) {
            throw new BusinessException(AuctionErrorCode.SELLER_CANNOT_BID);
        }
        if (bidderId.equals(this.highestBidderId)) {
            throw new BusinessException(AuctionErrorCode.ALREADY_HIGHEST_BIDDER);
        }
        // 상한 도입 전에 만든 경매는 다음 최소 입찰가가 상한을 넘을 수 있다. 입찰자 탓이 아니므로 따로 알린다
        if (minimumBidAmount() > MAX_PRICE) {
            throw new BusinessException(AuctionErrorCode.BID_LIMIT_REACHED);
        }
        if (amount > MAX_PRICE) {
            throw new BusinessException(AuctionErrorCode.BID_TOO_HIGH);
        }
        if (amount < minimumBidAmount()) {
            throw new BusinessException(AuctionErrorCode.BID_TOO_LOW);
        }
        if (this.buyNowPrice != null && amount >= this.buyNowPrice) {
            throw new BusinessException(AuctionErrorCode.BID_REACHES_BUY_NOW);
        }
        if (this.bidUnit > 0 && (amount - bidBasePrice()) % this.bidUnit != 0) {
            throw new BusinessException(AuctionErrorCode.BID_UNIT_MISMATCH);
        }
    }

    /**
     * 입찰 금액의 기준. 첫 입찰은 시작가, 이후는 현재가에서 입찰 단위만큼씩 올라간다.
     * (현재가를 기준으로 삼아, 규칙 도입 전에 단위와 맞지 않게 들어간 입찰이 있어도 다음 입찰이 막히지 않는다)
     */
    public long bidBasePrice() {
        requireBidding();
        return this.bidCount == 0 ? this.startPrice : this.currentPrice;
    }

    /** 즉시구매 전용 경매에는 입찰할 수 없다 (화면을 거치지 않은 요청 대비) */
    private void requireBidding() {
        if (isBuyNowOnly()) {
            throw new BusinessException(AuctionErrorCode.BIDDING_NOT_AVAILABLE);
        }
    }

    // ===== 마감 재시도 =====

    /**
     * 마감(정산 포함)에 실패했다. 다음 시도까지 기다릴 시간을 늘린다. (1초, 2초, 4초 ... 최대 5분)
     * @return 이번 실패로 재시도 한도에 닿았으면 true (스케줄러가 더 시도하지 않음)
     */
    public boolean recordCloseFailure(LocalDateTime now) {
        this.closeFailCount++;
        long delaySeconds = Math.min(1L << Math.min(this.closeFailCount - 1, 20), MAX_CLOSE_RETRY_DELAY_SECONDS);
        this.nextCloseAttemptAt = now.plusSeconds(delaySeconds);
        return this.closeFailCount >= MAX_CLOSE_FAILURES;
    }

    /**
     * 지금 입찰하면 바뀔 종료 시각. 마감 30초 전 이내면 입찰 시각 + 30초 (남은 시간이 다시 30초가 됨).
     * 정확히 30초 남았을 때는 그대로라 연장으로 보지 않는다. 마감을 앞당기는 일은 없다.
     * 엔티티를 바꾸지 않고 값만 계산한다. 조건부 UPDATE에 이 값을 넣어 원자적으로 반영하기 위해서다.
     */
    public LocalDateTime endAtAfterBid(LocalDateTime bidAt) {
        if (bidAt.isBefore(this.endAt.minusSeconds(BEFORE_DEADLINE_SECONDS))) {
            return this.endAt;
        }
        LocalDateTime refreshed = bidAt.plusSeconds(EXTENSION_SECONDS);
        return refreshed.isAfter(this.endAt) ? refreshed : this.endAt;
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
        this.product.sold();
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
