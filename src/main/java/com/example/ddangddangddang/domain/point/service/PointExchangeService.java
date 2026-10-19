package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointExchangeRequest;
import com.example.ddangddangddang.domain.point.dto.PointExchangeResponse;
import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 사용자 요청을 처리한다: 신청, 취소, 조회.
 * 실제 송금은 여기서 하지 않고 PointExchangeScheduler가 비동기로 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointExchangeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PointExchangeRepository pointExchangeRepository;
    private final PointExchangeLedger pointExchangeLedger;

    /**
     * 환전 신청. 포인트 차감, 신청 저장, 원장 기록이 한 트랜잭션으로 묶인다.
     * 저장된 PENDING 행이 곧 송금 작업 대기열 역할을 한다.
     */
    @Transactional
    public PointExchangeResponse requestExchange(Long userId, PointExchangeRequest request) {
        PointExchange exchange = PointExchange.create(
                userId,
                request.amount(),
                generatePayoutId(),
                request.bankCode(),
                request.accountNumber(),
                request.accountHolder()
        );

        // 원장에 환전 id를 남기려면 먼저 저장해서 id를 받아야 한다
        PointExchange saved = pointExchangeRepository.save(exchange);
        pointExchangeLedger.deduct(saved);

        return PointExchangeResponse.from(saved);
    }

    /** 사용자 취소. PENDING일 때만 가능하고, 차감했던 포인트를 돌려준다 */
    @Transactional
    public PointExchangeResponse cancelExchange(Long userId, Long exchangeId) {
        PointExchange exchange = pointExchangeRepository.findByIdForUpdate(exchangeId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.EXCHANGE_NOT_FOUND));
        exchange.validateOwner(userId);

        exchange.cancel();
        pointExchangeLedger.refund(exchange);

        return PointExchangeResponse.from(exchange);
    }

    public PointExchangeResponse getExchange(Long userId, Long exchangeId) {
        PointExchange exchange = pointExchangeRepository.findById(exchangeId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.EXCHANGE_NOT_FOUND));
        exchange.validateOwner(userId);
        return PointExchangeResponse.from(exchange);
    }

    public CursorResponse<PointExchangeResponse> getExchanges(Long userId, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        // 다음 페이지가 있는지 알기 위해 1개 더 조회한다
        List<PointExchange> fetched = pointExchangeRepository.findByCursor(
                userId, cursor, PageRequest.of(0, pageSize + 1));

        return CursorResponse.of(fetched, pageSize, PointExchangeResponse::from, PointExchange::getId);
    }

    private String generatePayoutId() {
        return "point-exchange-" + UUID.randomUUID();
    }
}
