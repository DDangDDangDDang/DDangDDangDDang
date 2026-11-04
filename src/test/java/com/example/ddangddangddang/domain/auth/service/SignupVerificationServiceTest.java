package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.message.EmailSender;
import com.example.ddangddangddang.global.message.SmsSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignupVerificationServiceTest {

    private static final String PHONE = "01012345678";

    @InjectMocks
    private SignupVerificationService signupVerificationService;

    @Mock
    private VerificationService verificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SmsSender smsSender;

    @Mock
    private EmailSender emailSender;

    @Test
    @DisplayName("인증번호를 만들어 문자로 보내고 만료까지 남은 시간(초)을 반환한다")
    void sendCode() {
        given(verificationService.issueCode(VerificationPurpose.SIGNUP_PHONE, PHONE)).willReturn("123456");

        long expiresIn = signupVerificationService.sendCode(PHONE);

        assertThat(expiresIn).isEqualTo(300);
        verify(smsSender).send(eq(PHONE), contains("123456"));
    }

    @Test
    @DisplayName("이미 가입된 번호면 인증번호를 만들지도 보내지도 않는다")
    void duplicatePhone() {
        given(userRepository.existsByPhoneNumber(PHONE)).willReturn(true);

        assertThatThrownBy(() -> signupVerificationService.sendCode(PHONE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.DUPLICATE_PHONE_NUMBER);
        verify(verificationService, never()).issueCode(any(), anyString());
        verify(smsSender, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("소셜 가입용은 이미 가입된 번호에도 인증번호를 보낸다 (기존 계정에 소셜 계정을 연결하므로)")
    void socialSignupAllowsRegisteredPhone() {
        given(verificationService.issueCode(VerificationPurpose.SIGNUP_PHONE, PHONE)).willReturn("123456");

        long expiresIn = signupVerificationService.sendCodeForSocialSignup(PHONE);

        assertThat(expiresIn).isEqualTo(300);
        verify(smsSender).send(eq(PHONE), contains("123456"));
        verify(userRepository, never()).existsByPhoneNumber(anyString());
    }

    @Test
    @DisplayName("가입할 이메일로 인증번호를 메일로 보낸다")
    void sendEmailCode() {
        given(verificationService.issueCode(VerificationPurpose.SIGNUP_EMAIL, "new@test.com")).willReturn("654321");

        long expiresIn = signupVerificationService.sendEmailCode("new@test.com");

        assertThat(expiresIn).isEqualTo(300);
        verify(emailSender).send(eq("new@test.com"), anyString(), contains("654321"));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 인증번호를 만들지도 보내지도 않는다")
    void duplicateEmail() {
        given(userRepository.existsByEmail("used@test.com")).willReturn(true);

        assertThatThrownBy(() -> signupVerificationService.sendEmailCode("used@test.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
        verify(verificationService, never()).issueCode(any(), anyString());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }
}
