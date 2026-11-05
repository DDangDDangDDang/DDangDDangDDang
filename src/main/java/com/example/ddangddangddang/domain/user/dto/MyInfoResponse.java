package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;

import java.time.LocalDateTime;

/**
 * 내 정보. 본인만 조회하므로 휴대폰 번호도 그대로
 *
 * @param nicknameChangeAvailableAt 다음에 닉네임을 바꿀 수 있는 시각. 지금 바로 바꿀 수 있으면 null
 * @param bio                       자기소개. 없으면 null
 * @param profileImageUrl           프로필 이미지 주소. 없으면 null (닉네임 첫 글자로 보여 준다)
 * @param hasPassword               비밀번호가 있는지. 소셜 로그인으로만 가입했으면 false
 *                                  (이메일·휴대폰 번호를 바꾸기 전 본인 확인을 비밀번호로 할지, 지금 휴대폰 번호로 할지 정한다)
 */
public record MyInfoResponse(
        Long userId,
        String email,
        String nickname,
        String phoneNumber,
        UserRole role,
        LocalDateTime createdAt,
        LocalDateTime nicknameChangeAvailableAt,
        String bio,
        String profileImageUrl,
        boolean hasPassword
) {

    public static MyInfoResponse of(User user, LocalDateTime nicknameChangeAvailableAt, String profileImageUrl) {
        return new MyInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCreatedAt(),
                nicknameChangeAvailableAt,
                user.getBio(),
                profileImageUrl,
                user.hasPassword()
        );
    }
}
