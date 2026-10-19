package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
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
}