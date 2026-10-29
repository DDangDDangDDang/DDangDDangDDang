package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.auth.verification.VerificationErrorCode;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.message.EmailSender;
import com.example.ddangddangddang.global.message.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserContactServiceTest {

    private UserContactService contactService;

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VerificationService verificationService;

    @Mock
    private EmailSender emailSender;

    @Mock
    private SmsSender smsSender;

    private User user;

    @BeforeEach
    void setUp() {
        contactService = new UserContactService(userService, userRepository, verificationService, emailSender, smsSender);
        user = User.create("user@test.com", "encoded", "경매왕", "01012345678");
        ReflectionTestUtils.setField(user, "id", 1L);
        lenient().when(userService.findUser(1L)).thenReturn(user);
    }

    @Nested
    @DisplayName("이메일 변경")
    class ChangeEmail {

        @Test
        @DisplayName("인증번호: 새 이메일로 보낸다")
        void sendCode() {
            given(verificationService.issueCode(VerificationPurpose.CHANGE_EMAIL, "new@test.com")).willReturn("123456");

            long expiresIn = contactService.sendEmailChangeCode(1L, "new@test.com");

            assertThat(expiresIn).isEqualTo(300);
            verify(emailSender).send(eq("new@test.com"), anyString(), contains("123456"));
        }

        @Test
        @DisplayName("인증번호: 지금 이메일이면 USER_010, 다른 회원이 쓰면 USER_001이고 보내지 않는다")
        void sendCodeRejected() {
            assertThatThrownBy(() -> contactService.sendEmailChangeCode(1L, "user@test.com"))
                    .extracting("errorCode").isEqualTo(UserErrorCode.SAME_EMAIL);

            given(userRepository.existsByEmail("other@test.com")).willReturn(true);
            assertThatThrownBy(() -> contactService.sendEmailChangeCode(1L, "other@test.com"))
                    .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_EMAIL);

            verify(verificationService, never()).issueCode(any(), anyString());
            verify(emailSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("인증번호가 맞으면 이메일을 바꾸고 새 토큰을 준다. 인증 토큰은 바로 쓴다")
        void change() {
            MyInfoUpdateResponse expected = new MyInfoUpdateResponse(null, "new-token", 1800);
            given(verificationService.verifyCode(VerificationPurpose.CHANGE_EMAIL, "new@test.com", "123456")).willReturn("token");
            given(userService.withNewAccessToken(user)).willReturn(expected);

            MyInfoUpdateResponse response = contactService.changeEmail(1L, "new@test.com", "123456");

            assertThat(user.getEmail()).isEqualTo("new@test.com");
            assertThat(response).isSameAs(expected);
            verify(verificationService).consumeToken(VerificationPurpose.CHANGE_EMAIL, "token");
            verify(userRepository).flush();
        }

        @Test
        @DisplayName("인증번호가 틀리면 VERIFY_001, 바꾸지 않는다")
        void invalidCode() {
            given(verificationService.verifyCode(VerificationPurpose.CHANGE_EMAIL, "new@test.com", "000000"))
                    .willThrow(new BusinessException(VerificationErrorCode.INVALID_CODE));

            assertThatThrownBy(() -> contactService.changeEmail(1L, "new@test.com", "000000"))
                    .extracting("errorCode").isEqualTo(VerificationErrorCode.INVALID_CODE);
            assertThat(user.getEmail()).isEqualTo("user@test.com");
        }

        @Test
        @DisplayName("동시에 같은 이메일로 바꿔 유니크 제약에 걸리면 USER_001")
        void uniqueViolationOnFlush() {
            given(verificationService.verifyCode(VerificationPurpose.CHANGE_EMAIL, "new@test.com", "123456")).willReturn("token");
            willThrow(new DataIntegrityViolationException("duplicate")).given(userRepository).flush();

            assertThatThrownBy(() -> contactService.changeEmail(1L, "new@test.com", "123456"))
                    .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Nested
    @DisplayName("휴대폰 번호 변경")
    class ChangePhoneNumber {

        @Test
        @DisplayName("인증번호: 새 번호로 문자를 보낸다")
        void sendCode() {
            given(verificationService.issueCode(VerificationPurpose.CHANGE_PHONE, "01099998888")).willReturn("654321");

            contactService.sendPhoneNumberChangeCode(1L, "01099998888");

            verify(smsSender).send(eq("01099998888"), contains("654321"));
        }

        @Test
        @DisplayName("인증번호: 지금 번호면 USER_011, 다른 회원이 쓰면 USER_004")
        void sendCodeRejected() {
            assertThatThrownBy(() -> contactService.sendPhoneNumberChangeCode(1L, "01012345678"))
                    .extracting("errorCode").isEqualTo(UserErrorCode.SAME_PHONE_NUMBER);

            given(userRepository.existsByPhoneNumber("01099998888")).willReturn(true);
            assertThatThrownBy(() -> contactService.sendPhoneNumberChangeCode(1L, "01099998888"))
                    .extracting("errorCode").isEqualTo(UserErrorCode.DUPLICATE_PHONE_NUMBER);
            verify(smsSender, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("휴대폰 번호가 없던 회원(소셜 가입 등)도 인증하면 번호를 등록할 수 있다")
        void changeWithoutPreviousNumber() {
            User social = User.createSocial("social@test.com", "소셜회원", null);
            ReflectionTestUtils.setField(social, "id", 2L);
            given(userService.findUser(2L)).willReturn(social);
            given(verificationService.verifyCode(VerificationPurpose.CHANGE_PHONE, "01099998888", "123456")).willReturn("token");
            MyInfoResponse expected = MyInfoResponse.of(social, null, null);
            given(userService.toMyInfo(social)).willReturn(expected);

            MyInfoResponse response = contactService.changePhoneNumber(2L, "01099998888", "123456");

            assertThat(social.getPhoneNumber()).isEqualTo("01099998888");
            assertThat(response).isSameAs(expected);
            verify(verificationService).consumeToken(VerificationPurpose.CHANGE_PHONE, "token");
        }
    }
}
