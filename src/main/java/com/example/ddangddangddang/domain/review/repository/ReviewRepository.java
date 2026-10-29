package com.example.ddangddangddang.domain.review.repository;


import com.example.ddangddangddang.domain.review.entity.Review;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderId(Long orderId);

    //구매자가 올린 리뷰순
    @Query(""" 
            select  r from Review  r where r.sellerId= :sellerId and (:cursor is null or r.id < :cursor) order by r.id desc""")
    List<Review> findSellerReviews(@Param("sellerId") Long sellerId, @Param("cursor") Long cursor, Pageable pageable);

    //판매자 리뷰 평균
    @Query("select avg(r.rating) from Review r where r.sellerId = :sellerId")
    Double findAverageRatingBySellerId(@Param("sellerId") Long sellerId);

    //리뷰 개수
    long countBySellerId(Long sellerId);




}
