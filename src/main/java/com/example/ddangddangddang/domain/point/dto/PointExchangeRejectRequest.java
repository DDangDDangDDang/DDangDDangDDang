package com.example.ddangddangddang.domain.point.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 반려.
 *
 * @param reason          반려 사유. 사용자에게 그대로 보이므로 이해할 수 있게 적는다. 예: "예금주가 일치하지 않습니다"
 * @param transferNotMade 송금 중(PROCESSING)인 신청을 반려할 때 "은행 이체를 하지 않았다"는 확인. 송금 중이면 true여야 한다.
 *                        이미 이체한 건을 반려하면 포인트도 돌려줘 돈과 포인트가 모두 나가기 때문이다. 송금 전(PENDING)이면 보지 않는다.
 */
public record PointExchangeRejectRequest(
        @NotBlank @Size(max = 200) String reason,
        Boolean transferNotMade
) {

    public boolean confirmsTransferNotMade() {
        return Boolean.TRUE.equals(transferNotMade);
    }
}
