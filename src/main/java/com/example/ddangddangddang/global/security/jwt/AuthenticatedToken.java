package com.example.ddangddangddang.global.security.jwt;

import org.springframework.security.core.Authentication;

import java.time.Instant;

/**
 * 검증을 통과한 Access Token의 인증 정보와, 이후 다시 검증할 때 필요한 토큰 ID(jti)·만료 시각.
 * WebSocket처럼 연결이 토큰 유효시간보다 오래 유지되는 경우에 쓴다.
 */
public record AuthenticatedToken(Authentication authentication, String tokenId, Instant expiresAt) {
}
