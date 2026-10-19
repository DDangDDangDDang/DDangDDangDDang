package com.example.ddangddangddang.support;

import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * 컨트롤러 통합 테스트에서 회원가입을 간단히 하기 위한 도우미.
 *
 * 실제 휴대폰 인증(Redis, 문자 발송)을 거치지 않도록 VerificationService를 Mock으로 두고,
 * "verified:{휴대폰 번호}" 형태의 토큰을 넣으면 그 번호로 인증된 것처럼 동작하게 한다.
 */
public final class SignupFixture {

    private static final String TOKEN_PREFIX = "verified:";

    private SignupFixture() {
    }

    /** 이메일마다 서로 다른 휴대폰 번호를 만든다. (휴대폰 번호 유니크 제약 때문) */
    public static String phoneOf(String email) {
        return "010" + String.format("%08d", Math.floorMod(email.hashCode(), 100_000_000));
    }

    public static String tokenOf(String phoneNumber) {
        return TOKEN_PREFIX + phoneNumber;
    }

    public static String signupBody(String email, String password, String nickname) {
        String phoneNumber = phoneOf(email);
        return """
                {"email": "%s", "password": "%s", "nickname": "%s", "phoneNumber": "%s", "phoneVerificationToken": "%s"}
                """.formatted(email, password, nickname, phoneNumber, tokenOf(phoneNumber));
    }

    /** "verified:{번호}" 토큰을 받으면 그 번호로 인증된 것으로 응답하게 한다. */
    public static void stubPhoneVerification(VerificationService verificationService) {
        given(verificationService.consumeToken(eq(VerificationPurpose.SIGNUP_PHONE), anyString()))
                .willAnswer(invocation -> {
                    String token = invocation.getArgument(1);
                    return token.startsWith(TOKEN_PREFIX) ? token.substring(TOKEN_PREFIX.length()) : "";
                });
    }
}