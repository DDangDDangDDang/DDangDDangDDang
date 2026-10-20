package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Nested
    @DisplayName("내 정보 조회")
    class GetMyInfo {

        @Test
        @DisplayName("이메일, 닉네임, 휴대폰 번호를 돌려준다")
        void success() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user()));

            MyInfoResponse response = userService.getMyInfo(1L);

            assertThat(response.userId()).isEqualTo(1L);
            assertThat(response.email()).isEqualTo("user@test.com");
            assertThat(response.nickname()).isEqualTo("경매왕");
            assertThat(response.phoneNumber()).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("토큰은 유효하지만 회원이 없으면(탈퇴 등) USER_003")
        void notFound() {
            given(userRepository.findById(1L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getMyInfo(1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("내 정보 수정")
    class UpdateMyInfo {

        @Test
        @DisplayName("닉네임을 바꾸고, 바뀐 닉네임이 담긴 새 Access Token을 돌려준다")
        void success() {
            User user = user();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByNickname("새닉네임")).willReturn(false);
            given(jwtProvider.createAccessToken(user)).willReturn("new-access-token");
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));

            MyInfoUpdateResponse response = userService.updateMyInfo(1L, new MyInfoUpdateRequest("새닉네임"));

            assertThat(user.getNickname()).isEqualTo("새닉네임");
            assertThat(response.user().nickname()).isEqualTo("새닉네임");
            assertThat(response.accessToken()).isEqualTo("new-access-token");
            assertThat(response.expiresIn()).isEqualTo(1800);
            verify(userRepository).flush();
        }

        @Test
        @DisplayName("지금과 같은 닉네임이면 중복 검사 없이 그대로 둔다 (자기 닉네임과 중복으로 보지 않음)")
        void sameNickname() {
            User user = user();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(user)).willReturn("new-access-token");
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));

            MyInfoUpdateResponse response = userService.updateMyInfo(1L, new MyInfoUpdateRequest("경매왕"));

            assertThat(response.user().nickname()).isEqualTo("경매왕");
            verify(userRepository, never()).existsByNickname(anyString());
            verify(userRepository, never()).flush();
        }

        @Test
        @DisplayName("다른 회원이 쓰는 닉네임이면 USER_002, 바꾸지 않는다")
        void duplicate() {
            User user = user();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByNickname("남의닉네임")).willReturn(true);

            assertThatThrownBy(() -> userService.updateMyInfo(1L, new MyInfoUpdateRequest("남의닉네임")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
            assertThat(user.getNickname()).isEqualTo("경매왕");
        }

        @Test
        @DisplayName("동시에 같은 닉네임으로 바꿔 유니크 제약에 걸리면 USER_002")
        void uniqueViolationOnFlush() {
            given(userRepository.findById(1L)).willReturn(Optional.of(user()));
            given(userRepository.existsByNickname("새닉네임")).willReturn(false);
            willThrow(new DataIntegrityViolationException("duplicate")).given(userRepository).flush();

            assertThatThrownBy(() -> userService.updateMyInfo(1L, new MyInfoUpdateRequest("새닉네임")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME);
        }
    }

    private User user() {
        User user = User.create("user@test.com", "encoded", "경매왕", "01012345678");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}