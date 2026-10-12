package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;

import java.time.LocalDateTime;

public record PointHistoryResponse(
        Long historyId,
        PointHistoryType type,
        long amount,
        long balanceAfter,
        long heldBalanceAfter,
        PointReferenceType refType,
        Long refId,
        LocalDateTime createdAt
) {

    public static PointHistoryResponse from(PointHistory history) {
        return new PointHistoryResponse(
                history.getId(),
                history.getType(),
                history.getAmount(),
                history.getBalanceAfter(),
                history.getHeldBalanceAfter(),
                history.getRefType(),
                history.getRefId(),
                history.getCreatedAt()
        );
    }
}