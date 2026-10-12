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

}