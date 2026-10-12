package com.example.ddangddangddang.global.security;

import com.example.ddangddangddang.domain.user.entity.UserRole;

/**
 * SecurityContext에 저장되는 인증 사용자 정보.
 * 컨트롤러에서는 @AuthenticationPrincipal AuthUser authUser로 꺼내 쓴다.
 * 요청마다 DB를 조회하지 않도록 Access Token의 클레임만으로 만든다.
 */
public record AuthUser(Long id, String email, String nickname, UserRole role) {
}