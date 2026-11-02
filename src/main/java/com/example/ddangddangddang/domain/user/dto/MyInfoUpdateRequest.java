package com.example.ddangddangddang.domain.user.dto;

import com.example.ddangddangddang.domain.user.entity.User;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정. 보낸 항목만 바꾸고, 보내지 않은(null) 항목은 그대로 둔다.
 * 이메일·휴대폰 번호는 본인 인증이 필요해 따로 바꾼다. (PUT /users/me/email, /users/me/phone-number)
 *
 * @param bio 자기소개. 빈 문자열이면 지운다
 */
public record MyInfoUpdateRequest(

        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "닉네임은 필수입니다.")
        String nickname,

        @Size(max = User.BIO_MAX_LENGTH, message = "소개는 150자 이하입니다.")
        String bio
) {
    public MyInfoUpdateRequest(String nickname) {
        this(nickname, null);
    }
}
