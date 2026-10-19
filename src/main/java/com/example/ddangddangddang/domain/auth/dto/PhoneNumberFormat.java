package com.example.ddangddangddang.domain.auth.dto;

/**
 * 휴대폰 번호 형식. 하이픈 없이 숫자만 받아 저장 형식(01012345678)과 같게 맞춘다.
 */
public final class PhoneNumberFormat {

    public static final String REGEX = "^01[016789]\\d{7,8}$";
    public static final String MESSAGE = "휴대폰 번호는 하이픈 없이 숫자만 입력해 주세요. (예: 01012345678)";

    private PhoneNumberFormat() {
    }
}