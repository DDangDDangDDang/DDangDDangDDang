package com.example.ddangddangddang.domain.order.dto;

import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderIssueType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 구매자의 문제 신고. 신고하면 자동 구매 확정이 멈추고 관리자가 확인한다. */
public record OrderIssueRequest(

        @NotNull(message = "문제 종류를 골라 주세요.")
        OrderIssueType type,

        @Size(max = Order.ISSUE_DETAIL_MAX_LENGTH, message = "신고 내용은 500자 이하입니다.")
        String detail
) {
}
