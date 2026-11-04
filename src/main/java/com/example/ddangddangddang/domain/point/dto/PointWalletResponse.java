package com.example.ddangddangddang.domain.point.dto;

import com.example.ddangddangddang.domain.point.entity.PointWallet;

/**
 * @param exchangeableBalance 지금 환전할 수 있는 포인트. 판매 정산·위약금으로 번 포인트 중 입찰에 묶이지 않은 만큼이다.
 *                            충전한 포인트는 환전할 수 없다. (결제 취소로만 돌려받는다)
 */
public record PointWalletResponse(long balance, long heldBalance, long totalBalance, long exchangeableBalance) {

    public static PointWalletResponse from(PointWallet wallet) {
        return new PointWalletResponse(
                wallet.getBalance(),
                wallet.getHeldBalance(),
                wallet.getBalance() + wallet.getHeldBalance(),
                wallet.getExchangeableBalance()
        );
    }
}
