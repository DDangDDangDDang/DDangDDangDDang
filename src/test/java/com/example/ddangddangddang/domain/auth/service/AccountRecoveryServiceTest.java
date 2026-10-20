package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.FindEmailResponse;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationErrorCode;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.message.EmailSender;
import com.example.ddangddangddang.global.message.SmsSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

    private static final String EMAIL = "user@test.com";
    private static final String PHONE = "01012345678";

    @InjectMocks
    private AccountRecoveryService accountRecoveryService;

    @Mock
    private VerificationService verificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SmsSender smsSender;

    @Mock
    private EmailSender emailSender;

    @Nested
    @DisplayName("이메일 찾기")
    class FindEmail {

        @Test
        @DisplayName("가입 여부와 상관없이 인증번호를 문자로 보낸다")
        void sendCode() {
            given(verificationService.issueCode(VerificationPurpose.FIND_EMAIL, PHONE)).willReturn("123456");

            accountRecoveryService.sendFindEmailCode(PHONE);

            verify(smsSender).send(eq(PHONE), contains("123456"));
        }

        @Test
        @DisplayName("인증되면 가린 이메일과 가입일을 반환한다")
        void success() {
            given(verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, "123456")).willReturn("token");
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.of(user()));

            FindEmailResponse response = accountRecoveryService.findEmail(PHONE, "123456");

            assertThat(response.maskedEmail()).isEqualTo("us**@test.com");
            assertThat(response.joinedAt()).isEqualTo(LocalDate.of(2026, 10, 1));
            verify(verificationService).consumeToken(VerificationPurpose.FIND_EMAIL, "token");
        }

        @Test
        @DisplayName("인증은 됐지만 가입된 계정이 없으면 USER_NOT_FOUND")
        void notRegistered() {
            given(verificationService.verifyCode(VerificationPurpose.FIND_EMAIL, PHONE, "123456")).willReturn("token");
            given(userRepository.findByPhoneNumber(PHONE)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountRecoveryService.findEmail(PHONE, "123456"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("비밀번호 재설정")
    class ResetPassword {

        @Test
        @DisplayName("가입된 이메일이면 인증번호를 메일로 보낸다")
        void sendCodeToRegistered() {
            given(verificationService.issueCode(VerificationPurpose.RESET_PASSWORD, EMAIL)).willReturn("123456");
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user()));

            accountRecoveryService.sendPasswordResetCode(EMAIL);

            verify(emailSender).send(eq(EMAIL), anyString(), contains("123456"));
        }

        @Test
        @DisplayName("가입되지 않은 이메일이면 메일은 보내지 않지만 인증번호 발급(재전송 제한)은 똑같이 한다")
        void sendCodeToUnregistered() {
            given(verificationService.issueCode(VerificationPurpose.RESET_PASSWORD, EMAIL)).willReturn("123456");
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

            long expiresIn = accountRecoveryService.sendPasswordResetCode(EMAIL);

            assertThat(expiresIn).isEqualTo(300);
            verify(emailSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("비밀번호를 바꾸고 Refresh Token을 지워 모든 기기에서 로그아웃시킨다")
        void reset() {
            User user = user();
            given(verificationService.consumeToken(VerificationPurpose.RESET_PASSWORD, "token")).willReturn(EMAIL);
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(passwordEncoder.encode("NewPass123!")).willReturn("new-encoded");

            accountRecoveryService.resetPassword("token", "NewPass123!");

            assertThat(user.getPassword()).isEqualTo("new-encoded");
            verify(refreshTokenRepository).deleteByUserId(1L);
        }

        @Test
        @DisplayName("인증 후 계정이 사라졌으면 VERIFY_004")
        void userGone() {
            given(verificationService.consumeToken(VerificationPurpose.RESET_PASSWORD, "token")).willReturn(EMAIL);
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

            assertThatThrownBy(() -> accountRecoveryService.resetPassword("token", "NewPass123!"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(VerificationErrorCode.INVALID_VERIFICATION_TOKEN);
            verify(refreshTokenRepository, never()).deleteByUserId(anyLong());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "user@test.com, us**@test.com",
            "ab@test.com, a*@test.com",
            "a@test.com, a@test.com",
            "longname@test.com, lo******@test.com"
    })
    @DisplayName("이메일 앞 1~2글자만 보이고 나머지는 가린다")
    void maskEmail(String email, String expected) {
        assertThat(AccountRecoveryService.maskEmail(email)).isEqualTo(expected);
    }

    private User user() {
        User user = User.create(EMAIL, "old-encoded", "경매왕", PHONE);
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.of(2026, 10, 1, 12, 0));
        return user;
    }
}