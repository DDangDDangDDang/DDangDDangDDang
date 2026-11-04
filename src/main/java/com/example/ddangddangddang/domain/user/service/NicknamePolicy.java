package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 닉네임에 쓸 수 없는 내용(욕설, 연락처, 외부 메신저 아이디 등)을 막는다. 채팅과 같은 규칙(ChatContentFilter)을 쓴다.
 * 닉네임은 채팅·방송·프로필마다 보이므로, 채팅에서 막는 내용을 닉네임으로 우회하지 못하게 한다.
 * 가입(AuthService), 소셜 가입(OAuthService), 닉네임 변경(UserService)에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class NicknamePolicy {

    private final ChatContentFilter contentFilter;

    public void validate(String nickname) {
        contentFilter.findViolation(nickname).ifPresent(violation -> {
            throw new BusinessException(UserErrorCode.NICKNAME_NOT_ALLOWED);
        });
    }
}
