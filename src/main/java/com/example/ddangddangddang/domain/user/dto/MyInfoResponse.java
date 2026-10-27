package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;

import java.time.LocalDateTime;

/**
 * 내 정보. 본인만 조회하므로 휴대폰 번호도 그대로
 *
 * @param nicknameChangeAvailableAt 다음에 닉네임을 바꿀 수 있는 시각. 지금 바로 바꿀 수 있으면 null
 */
public record MyInfoResponse(
        Long userId,
        String email,
        String nickname,
        String phoneNumber,
        UserRole role,
        LocalDateTime createdAt,
        LocalDateTime nicknameChangeAvailableAt
) {

    public static MyInfoResponse of(User user, LocalDateTime nicknameChangeAvailableAt) {
        return new MyInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCreatedAt(),
                nicknameChangeAvailableAt
        );
    }
}