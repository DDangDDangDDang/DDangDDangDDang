package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.AdminPointExchangeResponse;
import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.repository.PointExchangeRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 수동 지급 (point.payout.provider=manual).
 *
 * 흐름: 사용자 신청(PENDING, 포인트 차감됨)
 *      → 관리자 [송금 시작] (PROCESSING, 이후 사용자 취소 불가)
 *      → 관리자가 회사 계좌에서 은행 앱으로 직접 이체
 *      → 관리자 [지급 완료] (COMPLETE)
 *   또는 [반려] (FAILED, 포인트 환불). 송금 시작 전후 모두 가능하다.
 *
 * 송금 시작을 따로 두는 이유: 관리자가 이체하는 사이 사용자가 취소하면 포인트를 돌려받고 돈도 받게 된다.
 * 송금 시작으로 상태를 먼저 잠근 뒤에 이체해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPointExchangeService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PointExchangeRepository pointExchangeRepository;
    private final PointExchangeLedger pointExchangeLedger;

    /** 환전 신청 목록. 먼저 신청한 건부터 보여준다. status가 없으면 전체 */
    public CursorResponse<AdminPointExchangeResponse> getExchanges(PointExchangeType status, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        // 다음 페이지가 있는지 알기 위해 1개 더 조회한다
        List<PointExchange> fetched = pointExchangeRepository.findForAdmin(
                status, cursor, PageRequest.of(0, pageSize + 1));

        return CursorResponse.of(fetched, pageSize, AdminPointExchangeResponse::from, PointExchange::getId);
    }

    /** 송금 시작. 이체하기 전에 반드시 먼저 누른다 */
    @Transactional
    public AdminPointExchangeResponse startPayout(Long adminId, Long exchangeId) {
        PointExchange exchange = findForUpdate(exchangeId);
        exchange.startManualPayout(adminId, LocalDateTime.now());
        log.info("[수동지급] 송금 시작 exchangeId={}, adminId={}, amount={}", exchangeId, adminId, exchange.getAmount());
        return AdminPointExchangeResponse.from(exchange);
    }

    /** 지급 완료. 은행 이체가 끝난 걸 확인한 뒤에 누른다. 포인트는 신청 때 이미 차감되어 있다 */
    @Transactional
    public AdminPointExchangeResponse completePayout(Long adminId, Long exchangeId) {
        PointExchange exchange = findForUpdate(exchangeId);
        exchange.completeManualPayout(adminId);
        log.info("[수동지급] 지급 완료 exchangeId={}, adminId={}, amount={}", exchangeId, adminId, exchange.getAmount());
        return AdminPointExchangeResponse.from(exchange);
    }

    /** 반려. 차감했던 포인트를 돌려준다. 이미 이체한 건을 반려하면 돈과 포인트가 둘 다 나가니 주의 */
    @Transactional
    public AdminPointExchangeResponse reject(Long adminId, Long exchangeId, String reason) {
        PointExchange exchange = findForUpdate(exchangeId);
        exchange.reject(adminId, reason);
        pointExchangeLedger.refund(exchange);
        log.info("[수동지급] 반려 exchangeId={}, adminId={}, reason={}", exchangeId, adminId, reason);
        return AdminPointExchangeResponse.from(exchange);
    }

    /** 사용자 취소·다른 관리자 처리와 동시에 일어나도 하나만 성공하도록 행을 잠근다 */
    private PointExchange findForUpdate(Long exchangeId) {
        return pointExchangeRepository.findByIdForUpdate(exchangeId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.EXCHANGE_NOT_FOUND));
    }
}
