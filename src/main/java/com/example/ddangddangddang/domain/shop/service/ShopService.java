package com.example.ddangddangddang.domain.shop.service;

import com.example.ddangddangddang.domain.donation.service.DonationShopPayment;
import com.example.ddangddangddang.domain.shop.dto.ShopItemResponse;
import com.example.ddangddangddang.domain.shop.entity.ShopItem;
import com.example.ddangddangddang.domain.shop.entity.ShopPurchase;
import com.example.ddangddangddang.domain.shop.repository.ShopPurchaseRepository;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

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

    /** 상품 목록과 이 회원이 지금 살 수 있는지 */
    public List<ShopItemResponse> getItems(Long userId) {
        return Arrays.stream(ShopItem.values())
                .map(item -> switch (item) {
                    case NICKNAME_CHANGE -> userService.nicknameChangeAvailableAt(userId) == null
                            ? ShopItemResponse.unavailable(item, "지금은 변경권 없이 닉네임을 바꿀 수 있어요.")
                            : ShopItemResponse.available(item);
                })
                .toList();
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
