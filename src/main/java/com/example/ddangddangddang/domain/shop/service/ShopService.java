package com.example.ddangddangddang.domain.shop.service;

import com.example.ddangddangddang.domain.donation.service.DonationShopPayment;
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

    /** 상품 목록과 이 회원이 지금 살 수 있는지 */
    public List<ShopItemResponse> getItems(Long userId) {
        Set<NicknameBadge> ownedBadges = userBadgeService.owned(userId);
        return Arrays.stream(ShopItem.values())
                .map(item -> switch (item) {
                    case NICKNAME_CHANGE -> userService.nicknameChangeAvailableAt(userId) == null
                            ? ShopItemResponse.unavailable(item, "지금은 변경권 없이 닉네임을 바꿀 수 있어요.")
                            : ShopItemResponse.available(item);
                    case BADGE_ANGEL_WINGS, BADGE_DEVIL_WINGS, BADGE_HALO, BADGE_STAR -> ownedBadges.contains(item.badge())
                            ? ShopItemResponse.unavailable(item, "이미 가지고 있어요. 내 뱃지에서 장착할 수 있어요.")
                            : ShopItemResponse.available(item);
                })
                .toList();
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
