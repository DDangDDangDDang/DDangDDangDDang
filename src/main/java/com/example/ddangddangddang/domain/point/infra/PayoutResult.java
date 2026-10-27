package com.example.ddangddangddang.domain.point.infra;

public record PayoutResult(PayoutStatus status, String failReason) {

    public static PayoutResult success() {
        return new PayoutResult(PayoutStatus.SUCCESS, null);
    }

    public static PayoutResult failed(String reason) {
        return new PayoutResult(PayoutStatus.FAILED, reason);
    }

    public static PayoutResult inProgress() {
        return new PayoutResult(PayoutStatus.IN_PROGRESS, null);
    }

    public static PayoutResult notFound() {
        return new PayoutResult(PayoutStatus.NOT_FOUND, null);
    }
}
