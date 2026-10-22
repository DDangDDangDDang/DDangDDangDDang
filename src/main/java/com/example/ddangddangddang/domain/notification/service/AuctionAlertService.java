package com.example.ddangddangddang.domain.notification.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.notification.dto.AuctionAlertResponse;
import com.example.ddangddangddang.domain.notification.entity.AuctionAlert;
import com.example.ddangddangddang.domain.notification.exception.NotificationErrorCode;
import com.example.ddangddangddang.domain.notification.repository.AuctionAlertRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 경매 알림 받기·끄기 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionAlertService {

    private final AuctionRepository auctionRepository;
    private final AuctionAlertRepository auctionAlertRepository;
    private final TransactionTemplate transactionTemplate;

    public AuctionAlertResponse get(Long userId, Long auctionId) {
        if (!auctionRepository.existsById(auctionId)) {
            throw new BusinessException(AuctionErrorCode.NOT_FOUND);
        }
        return new AuctionAlertResponse(auctionId, auctionAlertRepository.existsByUserIdAndAuctionId(userId, auctionId));
    }

    /** 알림을 받는다. 이미 받기로 했으면 그대로 둔다. 시작 전·진행 중 경매만, 내 경매는 안 된다. (결과는 따로 알림) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AuctionAlertResponse subscribe(Long userId, Long auctionId) {
        Long sellerId = transactionTemplate.execute(status -> {
            Auction auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> new BusinessException(AuctionErrorCode.NOT_FOUND));
            if (auction.getStatus() != AuctionStatus.READY && auction.getStatus() != AuctionStatus.ONGOING) {
                throw new BusinessException(NotificationErrorCode.AUCTION_ALERT_UNAVAILABLE);
            }
            return auction.getProduct().getSeller().getId();
        });
        if (userId.equals(sellerId)) {
            throw new BusinessException(NotificationErrorCode.OWN_AUCTION_ALERT);
        }
        if (!auctionAlertRepository.existsByUserIdAndAuctionId(userId, auctionId)) {
            try {
                transactionTemplate.executeWithoutResult(status -> auctionAlertRepository.saveAndFlush(AuctionAlert.create(userId, auctionId)));
            } catch (DataIntegrityViolationException e) {
                // 같은 요청이 동시에 두 번 온 경우. 한쪽이 저장했으니 받기로 된 상태다.
            }
        }
        return new AuctionAlertResponse(auctionId, true);
    }

    /** 알림을 끈다. 받기로 하지 않았어도 성공으로 본다. */
    @Transactional
    public AuctionAlertResponse unsubscribe(Long userId, Long auctionId) {
        auctionAlertRepository.deleteMine(userId, auctionId);
        return new AuctionAlertResponse(auctionId, false);
    }
}
