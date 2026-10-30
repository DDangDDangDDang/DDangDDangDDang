package com.example.ddangddangddang.domain.notification.service;

import com.example.ddangddangddang.domain.auction.dto.response.AuctionListResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.notification.dto.AuctionAlertFilter;
import com.example.ddangddangddang.domain.notification.dto.AuctionAlertResponse;
import com.example.ddangddangddang.domain.notification.entity.AuctionAlert;
import com.example.ddangddangddang.domain.notification.exception.NotificationErrorCode;
import com.example.ddangddangddang.domain.notification.repository.AuctionAlertRepository;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 경매 알림 받기·끄기 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionAlertService {

    private final AuctionRepository auctionRepository;
    private final AuctionAlertRepository auctionAlertRepository;
    private final TransactionTemplate transactionTemplate;
    private final FileStorage fileStorage;

    static final int MAX_PAGE_SIZE = 50;

    /**
     * 내가 알림 받기를 한 경매. 최근에 받기로 한 것부터, 경매 목록 카드와 같은 형식으로 준다.
     * filter로 진행 중·예정(ACTIVE)과 끝난 경매(ENDED)를 나눠 본다. 끝난 경매는 끝난 지 7일이 지나면 정리된다. (NotificationCleaner)
     * 다음 페이지는 nextCursor를 cursor로 넘긴다. (경매가 지워졌으면 그 줄은 뺀다)
     */
    public CursorResponse<AuctionListResponse> getMine(Long userId, AuctionAlertFilter filter, Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        List<AuctionAlert> alerts = auctionAlertRepository.findMine(userId, filter.statuses(), cursor, PageRequest.of(0, pageSize + 1));
        Map<Long, Auction> auctions = auctionRepository.findAllById(alerts.stream().map(AuctionAlert::getAuctionId).toList())
                .stream().collect(Collectors.toMap(Auction::getId, Function.identity()));
        CursorResponse<AuctionListResponse> page = CursorResponse.of(alerts, pageSize,
                alert -> {
                    Auction auction = auctions.get(alert.getAuctionId());
                    return auction == null ? null : toListItem(auction);
                },
                AuctionAlert::getId);
        return new CursorResponse<>(page.content().stream().filter(Objects::nonNull).toList(), page.nextCursor(), page.hasNext());
    }

    private AuctionListResponse toListItem(Auction auction) {
        Product product = auction.getProduct();
        boolean live = auction.getLiveStream() != null && auction.getLiveStream().getStatus() == LiveStreamStatus.LIVE;
        return new AuctionListResponse(auction.getId(), product.getId(), product.getName(),
                product.getCategory() == null ? null : product.getCategory().getName(),
                auction.getCurrentPrice(), auction.getBidCount(), auction.getStatus(), auction.getStartAt(), auction.getEndAt(), live, false,
                product.getThumbnailKey() == null ? null : fileStorage.publicUrl(product.getThumbnailKey()));
    }

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
