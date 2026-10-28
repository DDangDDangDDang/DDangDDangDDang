package com.example.ddangddangddang.domain.shop.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.exception.AuctionErrorCode;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.donation.service.DonationShopPayment;
import com.example.ddangddangddang.domain.shop.dto.AuctionBoostResponse;
import com.example.ddangddangddang.domain.shop.entity.AuctionBoost;
import com.example.ddangddangddang.domain.shop.entity.ShopLock;
import com.example.ddangddangddang.domain.shop.exception.ShopErrorCode;
import com.example.ddangddangddang.domain.shop.repository.AuctionBoostRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import com.example.ddangddangddang.domain.shop.dto.ShopItemResponse;
import com.example.ddangddangddang.domain.shop.entity.ShopItem;
import com.example.ddangddangddang.domain.shop.entity.ShopPurchase;
import com.example.ddangddangddang.domain.shop.repository.ShopPurchaseRepository;
import com.example.ddangddangddang.domain.user.dto.MyBadgesResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.service.UserBadgeService;
import com.example.ddangddangddang.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 마일리지 상점: 도네이션 마일리지로 상품을 사고 바로 적용한다.
 * 결제와 효과 적용은 한 트랜잭션이다. (DonationShopPayment) 효과 적용이 실패하면 마일리지도 빠지지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ShopService {

    private final DonationShopPayment payment;
    private final ShopPurchaseRepository purchaseRepository;
    private final UserService userService;
    private final UserBadgeService userBadgeService;
    private final AuctionRepository auctionRepository;
    private final AuctionBoostRepository boostRepository;
    private final ShopLocks shopLocks;

    /** 상품 목록과 이 회원이 지금 살 수 있는지 */
    public List<ShopItemResponse> getItems(Long userId) {
        Set<NicknameBadge> ownedBadges = userBadgeService.owned(userId);
        String boostUnavailable = boostSlotsFullReason(boostRepository.findActive(LocalDateTime.now()), LocalDateTime.now());
        return Arrays.stream(ShopItem.values())
                .map(item -> switch (item) {
                    case NICKNAME_CHANGE -> userService.nicknameChangeAvailableAt(userId) == null
                            ? ShopItemResponse.unavailable(item, "지금은 변경권 없이 닉네임을 바꿀 수 있어요.")
                            : ShopItemResponse.available(item);
                    case BADGE_ANGEL_WINGS, BADGE_DEVIL_WINGS, BADGE_HALO, BADGE_STAR -> ownedBadges.contains(item.badge())
                            ? ShopItemResponse.unavailable(item, "이미 가지고 있어요. 내 뱃지에서 장착할 수 있어요.")
                            : ShopItemResponse.available(item);
                    case AUCTION_BOOST -> boostUnavailable != null
                            ? ShopItemResponse.unavailable(item, boostUnavailable)
                            : ShopItemResponse.available(item);
                })
                .toList();
    }

    /**
     * 경매 위로 올리기: 내 시작 전·진행 중 경매를 3시간 동안 경매 목록(최신순) 맨 위에 올린다.
     * 경매 하나는 한 번만, 동시에 올라가 있는 경매는 5개까지. 자리를 세고 저장하는 동안 상점 잠금 행을 잡아 6개가 되지 않게 한다.
     */
    public AuctionBoostResponse boostAuction(Long userId, Long auctionId) {
        shopLocks.prepare(ShopLock.AUCTION_BOOST);
        return payment.pay(userId, ShopItem.AUCTION_BOOST.price(), () -> {
            Auction auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> new BusinessException(AuctionErrorCode.NOT_FOUND));
            if (!auction.isSeller(userId)) {
                throw new BusinessException(ShopErrorCode.NOT_AUCTION_OWNER);
            }
            if (auction.getStatus() != AuctionStatus.READY && auction.getStatus() != AuctionStatus.ONGOING) {
                throw new BusinessException(ShopErrorCode.AUCTION_NOT_BOOSTABLE);
            }
            if (boostRepository.existsByAuctionId(auctionId)) {
                throw new BusinessException(ShopErrorCode.ALREADY_BOOSTED);
            }
            shopLocks.lock(ShopLock.AUCTION_BOOST);
            LocalDateTime now = LocalDateTime.now();
            String full = boostSlotsFullReason(boostRepository.findActive(now), now);
            if (full != null) {
                throw new BusinessException(ShopErrorCode.BOOST_SLOTS_FULL, full);
            }
            AuctionBoost boost;
            try {
                boost = boostRepository.saveAndFlush(AuctionBoost.start(auctionId, userId, now));
            } catch (DataIntegrityViolationException e) {
                // 같은 경매를 동시에 두 번 올리면 유니크(auction_id)가 막는다.
                throw new BusinessException(ShopErrorCode.ALREADY_BOOSTED);
            }
            ShopPurchase purchase = purchaseRepository.save(ShopPurchase.of(userId, ShopItem.AUCTION_BOOST, String.valueOf(auctionId)));
            return new DonationShopPayment.Charge<>(purchase.getId(), AuctionBoostResponse.from(boost));
        });
    }

    /** 자리가 다 찼으면 안내 문구 (가장 먼저 끝나는 홍보가 끝날 때까지 남은 분), 자리가 있으면 null */
    private static String boostSlotsFullReason(List<AuctionBoost> active, LocalDateTime now) {
        if (active.size() < AuctionBoost.MAX_ACTIVE) {
            return null;
        }
        long minutes = Math.max(1, (long) Math.ceil(Duration.between(now, active.get(0).getEndsAt()).toSeconds() / 60.0));
        return "지금은 위로 올릴 자리가 없어요. 약 %d분 뒤에 올릴 수 있어요.".formatted(minutes);
    }

    /** 닉네임 뱃지: 사면 영구로 가지고 바로 장착된다. 이미 가진 뱃지면 USER_014 (마일리지는 빠지지 않음) */
    public MyBadgesResponse buyBadge(Long userId, NicknameBadge badge) {
        ShopItem item = ShopItem.of(badge);
        return payment.pay(userId, item.price(), () -> {
            MyBadgesResponse badges = userBadgeService.grant(userId, badge);
            ShopPurchase purchase = purchaseRepository.save(ShopPurchase.of(userId, item, badge.name()));
            return new DonationShopPayment.Charge<>(purchase.getId(), badges);
        });
    }

    /** 닉네임 변경권: 사면서 바로 새 닉네임으로 바꾼다. 바뀐 닉네임이 담긴 새 Access Token을 함께 돌려준다. */
    public MyInfoUpdateResponse buyNicknameChange(Long userId, String nickname) {
        return payment.pay(userId, ShopItem.NICKNAME_CHANGE.price(), () -> {
            MyInfoUpdateResponse changed = userService.changeNicknameWithTicket(userId, nickname);
            ShopPurchase purchase = purchaseRepository.save(ShopPurchase.of(userId, ShopItem.NICKNAME_CHANGE, nickname));
            return new DonationShopPayment.Charge<>(purchase.getId(), changed);
        });
    }
}
