package com.example.ddangddangddang.domain.auth.dto;

import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;

public record LoginResponse(String accessToken, long expiresIn, UserInfo user) {

    public static LoginResponse of(String accessToken, long expiresIn, User user) {
        return new LoginResponse(accessToken, expiresIn, UserInfo.from(user));
    }

    public record UserInfo(Long userId, String nickname, UserRole role) {

        public static UserInfo from(User user) {
            return new UserInfo(user.getId(), user.getNickname(), user.getRole());
        }
    }
}