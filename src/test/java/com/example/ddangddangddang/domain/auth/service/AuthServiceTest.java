package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.SignupRequest;
import com.example.ddangddangddang.domain.auth.dto.SignupResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Nested
    @DisplayName("회원가입")
    class Signup {

        private final SignupRequest request = new SignupRequest("user@test.com", "Abcd1234!", "경매왕");

        @Test
        @DisplayName("성공하면 비밀번호를 암호화해서 저장한다")
        void success() {
            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(userRepository.existsByNickname(request.nickname())).willReturn(false);
            given(passwordEncoder.encode(request.password())).willReturn("encoded");
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

            SignupResponse response = authService.signup(request);

            assertThat(response.email()).isEqualTo("user@test.com");
            assertThat(response.nickname()).isEqualTo("경매왕");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("이메일이 중복되면 예외가 발생한다")
        void duplicateEmail() {
            given(userRepository.existsByEmail(request.email())).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_EMAIL);
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("닉네임이 중복되면 예외가 발생한다")
        void duplicateNickname() {
            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(userRepository.existsByNickname(request.nickname())).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
            verify(userRepository, never()).save(any());
        }
    }
}