package com.example.ddangddangddang.domain.product.entity;

import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    // 상품 이미지 키. 첫 장이 대표 이미지다. (product-images/{판매자 ID}/ 아래, ProductImageResolver)
    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "image_key", nullable = false, length = 200)
    private List<String> imageKeys = new ArrayList<>();

    // 대표 이미지 키(imageKeys의 첫 장). 경매 목록이 이미지 테이블을 조인하지 않고 바로 읽는다.
    @Column(name = "thumbnail_key", length = 200)
    private String thumbnailKey;

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

    //이미지 순서를 통째로 바꾼다. 대표 이미지도 첫 장으로 맞춘다.
    public void changeImages(List<String> imageKeys) {
        this.imageKeys.clear();
        this.imageKeys.addAll(imageKeys);
        this.thumbnailKey = imageKeys.isEmpty() ? null : imageKeys.get(0);
    }

    //상품 삭제(실제 삭제가 아닌 Status를 DELETED 전환)
    public void delete() {
        this.status = Status.DELETED;
    }

    //낙찰 또는 즉시구매로 판매 완료 (경매 C 파트에서 추가)
    public void sold() {
        this.status = Status.SOLD;
    }
}
