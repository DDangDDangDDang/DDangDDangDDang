package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 닉네임에 쓸 수 없는 내용(욕설, 연락처, 외부 메신저 아이디 등)을 막는다. 채팅과 같은 규칙(ChatContentFilter)을 쓴다.
 * 닉네임은 채팅·방송·프로필마다 보이므로, 채팅에서 막는 내용을 닉네임으로 우회하지 못하게 한다.
 * 가입(AuthService), 소셜 가입(OAuthService), 닉네임 변경(UserService)에서 부른다.
 */
@Component
@RequiredArgsConstructor
public class NicknamePolicy {

    // 보이지 않는 문자: 제어 문자(Cc)와 형식 문자(Cf, 폭 없는 공백 U+200B·방향 표시 등).
    // 한글 채움 문자(U+115F·U+1160·U+3164·U+FFA0)와 빈 점자(U+2800)는 글자로 분류돼 위 범주에 없지만 투명하게 보여 함께 막는다.
    // "씨(U+200B)발"처럼 금칙어 사이에 끼워 검사를 피하거나, 같아 보이는 다른 닉네임을 만드는 데 쓰인다.
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}\\u115F\\u1160\\u3164\\uFFA0\\u2800]");

    private final ChatContentFilter contentFilter;

    public void validate(String nickname) {
        if (INVISIBLE.matcher(nickname).find()) {
            throw new BusinessException(UserErrorCode.NICKNAME_NOT_ALLOWED);
        }
        contentFilter.findViolation(nickname).ifPresent(violation -> {
            throw new BusinessException(UserErrorCode.NICKNAME_NOT_ALLOWED);
        });
    }
}
