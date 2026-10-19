package com.example.ddangddangddang.domain.product.repository;


import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    //상품 조회: 삭제된 상품 제외 조회
    Optional<Product> findByIdAndStatusNot(Long id, Status status);
    // 내 판매 상품 조회 : 판매자가 나이면서 삭제되지 않은 상품(페이지 단위)
    Page<Product> findBySellerIdAndStatusNot(Long sellerId, Status status, Pageable pageable);
    // 카테고리 삭제 검증 : 카테고리 삭제전 카테고리 내 상품이 있는지 조회
    boolean existsByCategoryId(Long categoryId);

    //해당 카테고리 내 삭제된 상품을 제외한 상품이 있는지 조회(카테고리 생성 검증용)
    boolean existsByCategoryIdAndStatusNot(Long categoryId, Status status);

    //삭제 되지 않은 삭품 비관적락(쓰기 잠금)걸고 조회(경매 생성,상품 수정,삭제 동시 요청 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockByIdAndStatusNot(Long id,Status status);
}
