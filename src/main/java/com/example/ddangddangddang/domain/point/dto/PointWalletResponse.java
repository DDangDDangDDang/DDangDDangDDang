package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointWallet;

public record PointWalletResponse(long balance, long heldBalance, long totalBalance) {

    public static PointWalletResponse from(PointWallet wallet) {
        return new PointWalletResponse(
                wallet.getBalance(),
                wallet.getHeldBalance(),
                wallet.getBalance() + wallet.getHeldBalance()
        );
    }
}