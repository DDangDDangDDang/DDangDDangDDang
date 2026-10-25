package com.example.ddangddangddang.domain.seller.entity;

import com.example.ddangddangddang.domain.seller.exception.SellerErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;

/**
 * 사업자등록번호 (10자리). 하이픈은 빼고 숫자만 저장한다.
 * 국세청 검증 규칙(가중치 1,3,7,1,3,7,1,3,5와 마지막 검증 숫자)으로 형식이 맞는지 확인한다.
 * 실제로 등록·영업 중인지(휴폐업)는 확인하지 않는다. 관리자가 심사 때 확인한다.
 */
public final class BusinessNumber {

    public static final int LENGTH = 10;
    private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};

    private BusinessNumber() {
    }

    /** 하이픈·공백을 빼고 검증한 숫자 10자리. 비었으면 null(개인 판매자) */
    public static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String digits = input.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{" + LENGTH + "}") || !isValid(digits)) {
            throw new BusinessException(SellerErrorCode.INVALID_BUSINESS_NUMBER);
        }
        return digits;
    }

    static boolean isValid(String digits) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (digits.charAt(i) - '0') * WEIGHTS[i];
        }
        sum += ((digits.charAt(8) - '0') * 5) / 10;
        int check = (10 - sum % 10) % 10;
        return check == digits.charAt(9) - '0';
    }
}
