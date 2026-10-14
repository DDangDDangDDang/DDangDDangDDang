package com.example.ddangddangddang.domain.product.repository;


import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    //상품 조회: 삭제된 상품 제외 조회
    Optional<Product> findByIdAndStatusNot(Long id, Status status);
    // 내 판매 상품 조회 : 판매자가 나이면서 삭제되지 않은 상품(페이지 단위)
    Page<Product> findBySellerIdAndStatusNot(Long sellerId, Status status, Pageable pageable);
    // 카테고리 삭제 검증 : 카테고리 삭제전 카테고리 내 상품이 있는지 조회
    boolean existsByCategoryId(Long categoryId);

}
