package com.example.ddangddangddang.domain.donation.dto;

import com.example.ddangddangddang.domain.donation.entity.Donation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 방송 중인 판매자에게 도네이션 포인트를 선물한다.
 * - amount: 선물할 포인트. 최소·단위·최대는 선물 창 정보(GET .../donations/me)의 minGift·giftUnit·maxGift를 따른다.
 * - message: 선택. 선물과 함께 방송 화면에 보일 한마디 (50자 이하, 연락처·외부 링크 불가)
 * - requestKey: 선물 하나마다 새로 만드는 임의 값(UUID 등). 응답을 못 받아 같은 선물을 다시 보낼 때만 같은 값을 쓴다.
 *   같은 값으로 같은 선물을 다시 보내면(연타·재전송) 처음 결과를 그대로 돌려주고 두 번 선물하지 않는다.
 *   같은 값으로 다른 선물(금액·방송이 다름)을 보내면 DONATION_010으로 거부한다.
 */
public record DonationRequest(
        @NotNull(message = "선물할 포인트를 입력해 주세요.")
        Long amount,

        @Size(max = Donation.MAX_MESSAGE_LENGTH, message = "선물 메시지는 50자 이하입니다.")
        String message,

        @NotBlank(message = "요청 키가 필요합니다.")
        @Size(max = 64, message = "요청 키는 64자 이하입니다.")
        String requestKey
) {
}
