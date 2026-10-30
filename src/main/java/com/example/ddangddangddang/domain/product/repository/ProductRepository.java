package com.example.ddangddangddang.domain.product.repository;


import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    //상품 조회: 삭제된 상품 제외 조회
    Optional<Product> findByIdAndStatusNot(Long id, Status status);
    // 내 판매 상품 조회 + 상태·카테고리 거르기 (스튜디오 경매 상품 목록). status가 null이면 상태 무관, allCategories면 카테고리 무관
    @Query("select p from Product p where p.seller.id = :sellerId and p.status <> :deleted"
            + " and (:status is null or p.status = :status)"
            + " and (:allCategories = true or p.category.id in :categoryIds)")
    Page<Product> findMine(@Param("sellerId") Long sellerId, @Param("deleted") Status deleted, @Param("status") Status status,
                           @Param("allCategories") boolean allCategories, @Param("categoryIds") List<Long> categoryIds,
                           Pageable pageable);
    // 카테고리 삭제 검증 : 카테고리 삭제전 카테고리 내 상품이 있는지 조회
    boolean existsByCategoryId(Long categoryId);

    //해당 카테고리 내 삭제된 상품을 제외한 상품이 있는지 조회(카테고리 생성 검증용)
    boolean existsByCategoryIdAndStatusNot(Long categoryId, Status status);

    //삭제 되지 않은 삭품 비관적락(쓰기 잠금)걸고 조회(경매 생성,상품 수정,삭제 동시 요청 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockByIdAndStatusNot(Long id,Status status);
}
