package com.example.ddangddangddang.domain.review.entity;

import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name ="reviews",uniqueConstraints = @UniqueConstraint(name = "uk_reviews_order_id",columnNames = "order_id"),
        indexes = @Index(name ="idx_reviews_seller_id", columnList ="seller_id,id"))
public class Review extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long writerId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false, length = 100)
    private String productName;

    @Column(nullable = false)
    private int rating;

    @Column(length = 500)
    private String content;

    @Column(length = 300)
    private String replyContent;

    private LocalDateTime repliedAt;

    private Review(Order order, int rating, String content) {
        this.orderId = order.getId();
        this.writerId = order.getBuyerId();
        this.sellerId = order.getSellerId();
        this.productName = order.getProductName();
        this.rating = rating;
        this.content = normalizeContent(content);
    }

    public static Review create(Order order,int rating, String content){
        return new Review(order,rating,content);
    }

    //빈 내용("",' ')은 null로 통일 저장
    private static String  normalizeContent(String content){
        return (content== null || content.isBlank()) ? null : content;
    }

    public void update(int rating,String content){
        this.rating = rating;
        this.content =normalizeContent(content);
    }

    public void reply(String content){
        this.replyContent=content;
        this.repliedAt=LocalDateTime.now();
    }

    public void deleteReply(){
        this.replyContent=null;
        this.repliedAt=null;
    }

    public boolean hasReply(){
        return replyContent != null;
    }

    public boolean isWriter (Long userId){
        return writerId.equals(userId);
    }

    public boolean isSeller(Long userId){
        return  sellerId.equals(userId);
    }
}

