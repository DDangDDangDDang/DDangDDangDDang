package com.example.ddangddangddang.domain.review.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {

   REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND,"REVIEW_001","리뷰가 존재하지 않습니다."),
   NOT_REVIEW_WRITER(HttpStatus.FORBIDDEN,"REVIEW_002","본인이 쓴 리뷰만 수정,삭제가 할수 있습니다."),
   ORDER_NOT_COMPLETED(HttpStatus.CONFLICT,"REVIEW_003","구매 확정한 주문에만 리뷰를 쓸수 있습니다"),
   REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT,"REVIEW_004","이 부문에는 이미 리뷰를 작성을 했습니다"),
   NOT_REVIEW_SELLER(HttpStatus.FORBIDDEN,"REVIEW_005","리뷰를 받은 판매자만 답글을 달수 있습니다"),
   NOT_ORDER_BUYER(HttpStatus.FORBIDDEN,"REVIEW_006","본인 주문에만 리뷰를 쓸수 이습니다."),
   REPLY_NOT_FOUND(HttpStatus.NOT_FOUND,"REVIEW_007","답글이 존재하지 않습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}