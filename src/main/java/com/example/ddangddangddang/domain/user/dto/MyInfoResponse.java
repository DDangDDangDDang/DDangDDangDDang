package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;

import java.time.LocalDateTime;

/** 내 정보. 본인만 조회하므로 휴대폰 번호도 그대로 */
public record MyInfoResponse(
        Long userId,
        String email,
        String nickname,
        String phoneNumber,
        UserRole role,
        LocalDateTime createdAt
) {

    public static MyInfoResponse from(User user) {
        return new MyInfoResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhoneNumber(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}