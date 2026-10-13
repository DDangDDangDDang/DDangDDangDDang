package com.example.ddangddangddang.domain.auth.dto;

/**
 * 서비스에서 컨트롤러로 넘기는 로그인 결과.
 * Refresh Token은 응답 Body가 아니라 쿠키로 내려가야 해서 Body(LoginResponse)와 분리한다.
 */
public record LoginResult(LoginResponse response, String refreshToken) {
}