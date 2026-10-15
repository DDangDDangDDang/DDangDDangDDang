package com.example.ddangddangddang.domain.category.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name ="categories")
public class Category extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false,length = 20)
    private String name;

    @Column(nullable = false)
    private int sortOrder;
    //카테고리 생성(parent null일시 대분류, 값이 있으면 대분류의 하위 카테고리)
    public Category(Category parent, String name, int sortOrder) {
        this.parent = parent;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void update(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
    }


}
