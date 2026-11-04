package com.example.ddangddangddang.domain.auth.dto;

/**
 * 닉네임 형식. 가입·소셜 가입·닉네임 변경에 같은 규칙을 쓴다.
 * 앞뒤 공백을 막아 "abc"와 " abc"처럼 눈으로 구분되지 않는 닉네임이 생기지 않게 한다. (공백만 있는 값도 막힌다)
 */
public final class NicknameFormat {

    public static final String REGEX = "^\\S(?:.*\\S)?$";
    public static final String MESSAGE = "닉네임 앞뒤에는 공백을 넣을 수 없습니다.";

    private NicknameFormat() {
    }
}
