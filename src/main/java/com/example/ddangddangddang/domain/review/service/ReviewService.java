package com.example.ddangddangddang.domain.review.service;


import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.product.dto.response.ProductResponse;
import com.example.ddangddangddang.domain.review.dto.request.ReviewCreateRequest;
import com.example.ddangddangddang.domain.review.dto.request.ReviewReplyRequest;
import com.example.ddangddangddang.domain.review.dto.request.ReviewUpdateRequest;
import com.example.ddangddangddang.domain.review.dto.response.ReviewListResponse;
import com.example.ddangddangddang.domain.review.dto.response.ReviewResponse;
import com.example.ddangddangddang.domain.review.entity.Review;
import com.example.ddangddangddang.domain.review.exception.ReviewErrorCode;
import com.example.ddangddangddang.domain.review.repository.ReviewRepository;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.CacheResponse;
import java.util.List;

@RequiredArgsConstructor
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private static final int MAX_PAGE_SIZE =30;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    // 구매자 리뷰 작성
    @Transactional
    public ReviewResponse createReview(Long userId, ReviewCreateRequest request){
        Order order = orderRepository.findById(request.orderId()).orElseThrow(
                () -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND)
        );

        if (!order.isBuyer(userId)){
            throw new BusinessException(ReviewErrorCode.NOT_ORDER_BUYER);
        }
        if (order.getStatus() != OrderStatus.COMPLETED){
            throw new BusinessException(ReviewErrorCode.ORDER_NOT_COMPLETED);
        }
        if (reviewRepository.existsByOrderId(order.getId())) {
            throw new BusinessException(ReviewErrorCode.REVIEW_ALREADY_EXISTS);
        }
        Review review = Review.create(order, request.rating(), request.content());
        return ReviewResponse.from(reviewRepository.save(review));
    }


    // 리뷰수정
    @Transactional
    public ReviewResponse updateReview(Long userId, Long reviewId, @Valid ReviewUpdateRequest request) {
       Review review= findReview(reviewId);

       if (!review.isWriter(userId)) {
           throw new BusinessException(ReviewErrorCode.NOT_REVIEW_WRITER);
       }

       review.update(request.rating(), request.content());;
       return ReviewResponse.from(review);
    }

    // 구매자 리뷰 삭제
    @Transactional
    public void deleteReview(Long userId, Long reviewId) {
        Review review = findReview(reviewId);
        if (!review.isWriter(userId)){
            throw  new BusinessException(ReviewErrorCode.NOT_REVIEW_WRITER);
        }
        reviewRepository.delete(review);
    }

    // 판매자 리뷰 목록(상점 리뷰 탭,로그인 없이 조회)
    public CursorResponse<ReviewResponse> getSellerReviews(Long sellerId, Long cursor, int size) {
        if (!userRepository.existsById(sellerId)){
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<Review> reviews = reviewRepository.findSellerReviews(sellerId, cursor, PageRequest.of(0, pageSize + 1));

        return CursorResponse.of(reviews,pageSize,ReviewResponse::from,Review::getId);

    }

    //판매자 답글 달기,수정
    @Transactional
    public ReviewResponse replyReview(Long userId, Long reviewId, ReviewReplyRequest request) {
        Review review = findReview(reviewId);
        if (!review.isSeller(userId)){
            throw new BusinessException(ReviewErrorCode.NOT_REVIEW_SELLER);
        }
        review.reply(request.content());
        return ReviewResponse.from(review);
    }

    private Review findReview(Long reviewId){
        return reviewRepository.findById(reviewId).orElseThrow(
                ()-> new BusinessException(ReviewErrorCode.REVIEW_NOT_FOUND)
        );
    }
}
