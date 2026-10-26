package com.example.ddangddangddang.domain.auth.oauth;

/** 카카오에서 받은 회원 정보
 * @param id        카카오 회원번호
 * @param email     카카오가 검증한 이메일만 담는다.
 * @param nickname  카카오 닉네임
 */
public record SocialUser(
        String id,
        String email,
        String nickname
) {
}
