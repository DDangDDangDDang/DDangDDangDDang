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

    public Product(User seller, Category category, String name, String description, Status status) {
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

    public void deldte () {
        this.status = Status.DELETED;
    }
}
