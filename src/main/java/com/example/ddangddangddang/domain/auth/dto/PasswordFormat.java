package com.example.ddangddangddang.domain.auth.dto;

/**
 * 비밀번호 규칙, 회원가입과 재설정에 같은 규칙을 사용
 */
public final class PasswordFormat {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,20}$";
    public static final String MESSAGE = "비밀번호는 8~20자이며 영문, 숫자, 특수문자를 모두 포함해야 합니다.";

    private PasswordFormat() {
    }
}
