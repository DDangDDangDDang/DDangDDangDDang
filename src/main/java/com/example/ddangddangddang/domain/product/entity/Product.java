package com.example.ddangddangddang.domain.product.entity;

import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name ="products")
public class Product extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id",nullable = false)
    private Category category;

    @Column(nullable = false,length = 50)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    private Status status;

    //상품 생성시 항상 Status==ACTIVE 시작된다
    public Product(User seller, Category category, String name, String description) {
        this.seller = seller;
        this.category = category;
        this.name = name;
        this.description = description;
        this.status = Status.ACTIVE;
    }

    public void update(Category category, String name, String description) {
        this.category = category;
        this.name = name;
        this.description = description;
    }

    //상품 삭제(실제 삭제가 아닌 Status를 DELETED 전환)
    public void delete() {
        this.status = Status.DELETED;
    }

    //낙찰 시 상품 판매 완료 처리 (경매 마감에서 호출)
    public void markSold(){
        this.status = Status.SOLD;
    }


}
