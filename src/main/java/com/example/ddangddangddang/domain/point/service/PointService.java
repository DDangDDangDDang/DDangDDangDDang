package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ddangddangddang.domain.point.dto.PointHistoryResponse;
import com.example.ddangddangddang.domain.point.dto.PointWalletResponse;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private static final int MAX_PAGE_SIZE = 100;
    private final PointWalletRepository pointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 회원가입과 같은 트랜잭션에서 호출된다. 지갑 생성에 실패하면 회원가입도 함께 롤백된다.
     */
    @Transactional
    public void createWallet(Long userId) {
        if (pointWalletRepository.existsByUserId(userId)) {
            throw new BusinessException(PointErrorCode.WALLET_ALREADY_EXISTS);
        }
        pointWalletRepository.save(PointWallet.create(userId));
    }

    public PointWalletResponse getWallet(Long userId) {
        return PointWalletResponse.from(findWallet(userId));
    }

    public CursorResponse<PointHistoryResponse> getHistories(Long userId, PointHistoryType type, Long cursor, int size) {
        PointWallet wallet = findWallet(userId);
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        // 다음 페이지가 있는지 알기 위해 1개 더 조회한다
        List<PointHistory> fetched = pointHistoryRepository.findByCursor(
                wallet.getId(), type, cursor, PageRequest.of(0, pageSize + 1));

        return CursorResponse.of(fetched, pageSize, PointHistoryResponse::from, PointHistory::getId);
    }

    private PointWallet findWallet(Long userId) {
        return pointWalletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));
    }

    /** 입찰: 사용 가능 포인트를 입찰 금액만큼 홀딩한다. (첫 입찰처럼 이전 최고 입찰자가 없을 때) */
    @Transactional
    public void hold(Long userId, long amount, Long auctionId) {
        PointWallet wallet = findWalletForUpdate(userId);
        wallet.hold(amount);
        record(wallet, PointHistoryType.HOLD, amount, PointReferenceType.AUCTION, auctionId);
    }

    /** 홀딩 해제: 경매 취소, 즉시구매로 종료될 때 기존 최고 입찰자의 홀딩을 돌려준다. */
    @Transactional
    public void release(Long userId, long amount, Long auctionId) {
        PointWallet wallet = findWalletForUpdate(userId);
        wallet.release(amount);
        record(wallet, PointHistoryType.RELEASE, amount, PointReferenceType.AUCTION, auctionId);
    }

    /**
     * 상위 입찰: 이전 최고 입찰자의 홀딩을 해제하고 새 입찰자의 포인트를 홀딩한다.
     *
     * 지갑 두 개를 함께 잠그므로 잠그는 순서가 중요하다.
     * A가 B를, 동시에 B가 A를 역전하는 입찰이 들어오면 서로 상대 지갑을 기다리는 데드락이 생긴다.
     * 항상 userId가 작은 지갑부터 잠가서 순서를 통일한다.
     */
    @Transactional
    public void replaceHold(Long newBidderId, long newAmount, Long prevBidderId, long prevAmount, Long auctionId) {
        Map<Long, PointWallet> wallets = findWalletsForUpdate(newBidderId, prevBidderId);

        PointWallet prevWallet = wallets.get(prevBidderId);
        prevWallet.release(prevAmount);
        record(prevWallet, PointHistoryType.RELEASE, prevAmount, PointReferenceType.AUCTION, auctionId);

        PointWallet newWallet = wallets.get(newBidderId);
        newWallet.hold(newAmount);
        record(newWallet, PointHistoryType.HOLD, newAmount, PointReferenceType.AUCTION, auctionId);
    }

    /** 낙찰: 홀딩된 포인트를 차감한다. 차감된 포인트는 주문(에스크로)에 묶인다. */
    @Transactional
    public void purchaseWithHeld(Long userId, long amount, Long auctionId) {
        PointWallet wallet = findWalletForUpdate(userId);
        wallet.purchaseWithHeld(amount);
        record(wallet, PointHistoryType.PURCHASE, amount, PointReferenceType.AUCTION, auctionId);
    }

    /** 즉시구매: 사용 가능 포인트를 바로 차감한다. */
    @Transactional
    public void purchase(Long userId, long amount, Long auctionId) {
        PointWallet wallet = findWalletForUpdate(userId);
        wallet.purchase(amount);
        record(wallet, PointHistoryType.PURCHASE, amount, PointReferenceType.AUCTION, auctionId);
    }

    /** 구매 확정: 판매자에게 낙찰 금액을 정산한다. */
    @Transactional
    public void settle(Long sellerId, long amount, Long orderId) {
        PointWallet wallet = findWalletForUpdate(sellerId);
        wallet.settle(amount);
        record(wallet, PointHistoryType.SETTLE, amount, PointReferenceType.ORDER, orderId);
    }

    // private 메서드 추가
    private PointWallet findWalletForUpdate(Long userId) {
        return pointWalletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));
    }

    private Map<Long, PointWallet> findWalletsForUpdate(Long... userIds) {
        Map<Long, PointWallet> wallets = new HashMap<>();
        Stream.of(userIds).distinct().sorted()
                .forEach(userId -> wallets.put(userId, findWalletForUpdate(userId)));
        return wallets;
    }

    private void record(PointWallet wallet, PointHistoryType type, long amount,
                        PointReferenceType refType, Long refId) {
        pointHistoryRepository.save(PointHistory.of(wallet, type, amount, refType, refId));
    }
}