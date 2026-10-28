package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livechat.moderation.ChatModerationProperties;
import com.example.ddangddangddang.domain.user.config.UserPolicyProperties;
import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.storage.FileStorage;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);
    private static final Duration INTERVAL = Duration.ofDays(30);

    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private FileStorage fileStorage;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Clock clock = Clock.fixed(NOW.atZone(zone).toInstant(), zone);
        ChatContentFilter contentFilter = new ChatContentFilter(new ChatModerationProperties(List.of(), null));
        userService = new UserService(userRepository, jwtProvider, new UserPolicyProperties(INTERVAL), fileStorage, contentFilter, clock);
    }

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
            assertThat(response.nicknameChangeAvailableAt()).isNull();
        }

        @Test
        @DisplayName("닉네임을 바꾼 지 30일이 안 됐으면 다음에 바꿀 수 있는 시각을 돌려준다")
        void nicknameChangeAvailableAt() {
            User user = user();
            user.changeNickname("바꾼닉네임", NOW.minusDays(10));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThat(userService.getMyInfo(1L).nicknameChangeAvailableAt()).isEqualTo(NOW.plusDays(20));
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
            assertThat(response.user().nicknameChangeAvailableAt()).isEqualTo(NOW.plus(INTERVAL));
            verify(userRepository).flush();
        }

        @Test
        @DisplayName("닉네임을 바꾼 지 30일이 안 됐으면 USER_005, 중복 검사도 하지 않고 바꾸지 않는다")
        void tooSoon() {
            User user = user();
            user.changeNickname("바꾼닉네임", NOW.minus(INTERVAL).plusMinutes(1));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.updateMyInfo(1L, new MyInfoUpdateRequest("새닉네임")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.NICKNAME_CHANGE_TOO_SOON);
            assertThat(user.getNickname()).isEqualTo("바꾼닉네임");
            verify(userRepository, never()).existsByNickname(anyString());
        }

        @Test
        @DisplayName("닉네임을 바꾼 지 30일이 지났으면 다시 바꿀 수 있다")
        void afterInterval() {
            User user = user();
            user.changeNickname("바꾼닉네임", NOW.minus(INTERVAL));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(userRepository.existsByNickname("새닉네임")).willReturn(false);
            given(jwtProvider.createAccessToken(user)).willReturn("new-access-token");
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));

            userService.updateMyInfo(1L, new MyInfoUpdateRequest("새닉네임"));

            assertThat(user.getNickname()).isEqualTo("새닉네임");
            assertThat(user.getNicknameChangedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("기간 안이라도 지금과 같은 닉네임으로 저장하면 막지 않는다 (바뀌는 것이 없음)")
        void sameNicknameWithinInterval() {
            User user = user();
            user.changeNickname("바꾼닉네임", NOW.minusDays(1));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(user)).willReturn("new-access-token");
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));

            MyInfoUpdateResponse response = userService.updateMyInfo(1L, new MyInfoUpdateRequest("바꾼닉네임"));

            assertThat(response.user().nicknameChangeAvailableAt()).isEqualTo(NOW.plusDays(29));
            assertThat(user.getNicknameChangedAt()).isEqualTo(NOW.minusDays(1));
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

    @Nested
    @DisplayName("소개 수정")
    class UpdateBio {

        @Test
        @DisplayName("소개만 보내면 소개만 바꾸고 닉네임은 그대로 둔다 (앞뒤 공백은 지운다)")
        void bioOnly() {
            User user = user();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));

            MyInfoUpdateResponse response = userService.updateMyInfo(1L, new MyInfoUpdateRequest(null, "  빈티지 카메라 모아요  "));

            assertThat(response.user().bio()).isEqualTo("빈티지 카메라 모아요");
            assertThat(user.getNickname()).isEqualTo("경매왕");
            assertThat(user.getNicknameChangedAt()).isNull();
            verify(userRepository, never()).existsByNickname(anyString());
        }

        @Test
        @DisplayName("빈 소개를 보내면 지운다")
        void clear() {
            User user = user();
            user.changeBio("예전 소개");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(jwtProvider.getAccessTokenValidity()).willReturn(Duration.ofMinutes(30));

            userService.updateMyInfo(1L, new MyInfoUpdateRequest(null, "  "));

            assertThat(user.getBio()).isNull();
        }

        @Test
        @DisplayName("연락처로 외부 거래를 유도하면 USER_006, 바꾸지 않는다")
        void contentNotAllowed() {
            User user = user();
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.updateMyInfo(1L, new MyInfoUpdateRequest(null, "문의 010-1234-5678")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.CONTENT_NOT_ALLOWED);
            assertThat(user.getBio()).isNull();
        }
    }

    @Nested
    @DisplayName("프로필 이미지")
    class ProfileImage {

        private static final String KEY = "profile-images/1/123e4567-e89b-12d3-a456-426614174000.png";

        @Test
        @DisplayName("업로드 URL: 내 회원 ID가 들어간 키로 형식·크기를 정해 발급한다")
        void uploadUrl() {
            given(fileStorage.presignUpload(startsWith("profile-images/1/"), eq("image/png"), eq(1000L), any()))
                    .willReturn("https://upload.example/signed");

            ProfileImageUploadResponse response =
                    userService.createProfileImageUploadUrl(1L, new ProfileImageUploadRequest("image/png", 1000L));

            assertThat(response.uploadUrl()).isEqualTo("https://upload.example/signed");
            assertThat(response.imageKey()).matches("profile-images/1/[0-9a-f-]{36}\\.png");
            assertThat(response.expiresIn()).isEqualTo(300);
        }

        @Test
        @DisplayName("업로드 URL: 이미지가 아니면 USER_007, 5MB를 넘으면 USER_008")
        void uploadUrlRejected() {
            assertThatThrownBy(() -> userService.createProfileImageUploadUrl(1L, new ProfileImageUploadRequest("image/svg+xml", 1000L)))
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);
            assertThatThrownBy(() -> userService.createProfileImageUploadUrl(1L,
                    new ProfileImageUploadRequest("image/png", UserService.PROFILE_IMAGE_MAX_BYTES + 1)))
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
            verify(fileStorage, never()).presignUpload(anyString(), anyString(), anyLong(), any());
        }

        @Test
        @DisplayName("확정: 올라간 이미지로 바꾸고 이전 이미지는 지운다")
        void change() {
            User user = user();
            user.changeProfileImage("profile-images/1/old.png");
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(fileStorage.find(KEY)).willReturn(Optional.of(new FileStorage.StoredFile(1000, "image/png")));
            given(fileStorage.publicUrl(KEY)).willReturn("https://cdn.example/" + KEY);

            MyInfoResponse response = userService.changeProfileImage(1L, KEY);

            assertThat(user.getProfileImageKey()).isEqualTo(KEY);
            assertThat(response.profileImageUrl()).isEqualTo("https://cdn.example/" + KEY);
            verify(fileStorage).delete("profile-images/1/old.png");
        }

        @Test
        @DisplayName("확정: 남의 키(다른 회원 ID)나 형식이 다른 키면 저장소를 보지도 않고 USER_009")
        void othersKey() {
            assertThatThrownBy(() -> userService.changeProfileImage(1L, "profile-images/2/123e4567-e89b-12d3-a456-426614174000.png"))
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.PROFILE_IMAGE_NOT_UPLOADED);
            assertThatThrownBy(() -> userService.changeProfileImage(1L, "profile-images/1/../2/a.png"))
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.PROFILE_IMAGE_NOT_UPLOADED);
            verify(fileStorage, never()).find(anyString());
        }

        @Test
        @DisplayName("확정: 아직 올리지 않았으면 USER_009, 올라간 파일이 너무 크면 USER_008")
        void notUploadedOrTooLarge() {
            given(fileStorage.find(KEY)).willReturn(Optional.empty());
            assertThatThrownBy(() -> userService.changeProfileImage(1L, KEY))
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.PROFILE_IMAGE_NOT_UPLOADED);

            given(fileStorage.find(KEY)).willReturn(Optional.of(
                    new FileStorage.StoredFile(UserService.PROFILE_IMAGE_MAX_BYTES + 1, "image/png")));
            assertThatThrownBy(() -> userService.changeProfileImage(1L, KEY))
                    .extracting("errorCode")
                    .isEqualTo(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }

        @Test
        @DisplayName("삭제: 프로필 이미지를 비우고 파일을 지운다")
        void delete() {
            User user = user();
            user.changeProfileImage(KEY);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));

            MyInfoResponse response = userService.deleteProfileImage(1L);

            assertThat(response.profileImageUrl()).isNull();
            verify(fileStorage).delete(KEY);
        }
    }

    private User user() {
        User user = User.create("user@test.com", "encoded", "경매왕", "01012345678");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}