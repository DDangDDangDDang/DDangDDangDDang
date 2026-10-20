package com.example.ddangddangddang.domain.user.dto;

/**
 * 수정된 내 정보와 새 Access Token.
 * Access Token 안에 닉네임이 들어 있어서(라이브 채팅 등에서 사용), 바뀐 닉네임이 담긴 토큰으로 바로 교체하게 한다.
 */
public record MyInfoUpdateResponse(MyInfoResponse user, String accessToken, long expiresIn) {
}