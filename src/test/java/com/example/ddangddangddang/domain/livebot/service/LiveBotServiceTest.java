package com.example.ddangddangddang.domain.livebot.service;

import com.example.ddangddangddang.domain.livebot.dto.LiveBotCreateRequest;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotResponse;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotStartRequest;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotVideoUploadRequest;
import com.example.ddangddangddang.domain.livebot.dto.LiveBotVideoUploadResponse;
import com.example.ddangddangddang.domain.livebot.entity.LiveBot;
import com.example.ddangddangddang.domain.livebot.exception.LiveBotErrorCode;
import com.example.ddangddangddang.domain.livebot.repository.LiveBotRepository;
import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadRequest;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.domain.user.service.UserService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.ErrorCode;
import com.example.ddangddangddang.global.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LiveBotServiceTest {

    private static final String KEY = "live-bots/123e4567-e89b-12d3-a456-426614174000.mp4";
    private static final Long BOT_ID = 3L;
    private static final Long USER_ID = 30L;

    @InjectMocks
    private LiveBotService liveBotService;

    @Mock
    private LiveBotRepository liveBotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LiveStreamRepository liveStreamRepository;
    @Mock
    private LiveStreamService liveStreamService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private UserService userService;

    private static void assertError(Executable executable, ErrorCode code) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }

    private LiveBot bot() {
        LiveBot bot = LiveBot.create(USER_ID, KEY);
        ReflectionTestUtils.setField(bot, "id", BOT_ID);
        return bot;
    }

    @Test
    @DisplayName("업로드 URL: 500MB 이하 MP4·WEBM이면 live-bots/ 아래 키를 만든다")
    void createVideoUpload() {
        given(fileStorage.presignUpload(anyString(), eq("video/mp4"), eq(1000L), any())).willReturn("https://upload");
        given(fileStorage.publicUrl(anyString())).willReturn("https://cdn/video.mp4");

        LiveBotVideoUploadResponse response = liveBotService.createVideoUpload(new LiveBotVideoUploadRequest("video/mp4", 1000L));

        assertThat(response.videoKey()).startsWith("live-bots/").endsWith(".mp4");
        assertThat(response.uploadUrl()).isEqualTo("https://upload");
    }

    @Test
    @DisplayName("업로드 URL: 영상이 아니거나 500MB를 넘으면 거절")
    void createVideoUploadInvalid() {
        assertError(() -> liveBotService.createVideoUpload(new LiveBotVideoUploadRequest("video/quicktime", 1000L)), LiveBotErrorCode.INVALID_VIDEO);
        assertError(() -> liveBotService.createVideoUpload(new LiveBotVideoUploadRequest("video/mp4", LiveBotService.MAX_VIDEO_BYTES + 1)),
                LiveBotErrorCode.INVALID_VIDEO);
    }

    @Test
    @DisplayName("봇 만들기: 로그인할 수 없는 계정(비밀번호 없음, .invalid 이메일)을 만들고 영상과 묶는다")
    void create() {
        given(fileStorage.find(KEY)).willReturn(Optional.of(new FileStorage.StoredFile(1000L, "video/mp4")));
        given(userRepository.existsByNickname("땅땅봇")).willReturn(false);
        given(userRepository.save(any())).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", USER_ID);
            return user;
        });
        given(liveBotRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        LiveBotResponse response = liveBotService.create(new LiveBotCreateRequest(" 땅땅봇 ", KEY));

        ArgumentCaptor<User> user = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(user.capture());
        assertThat(user.getValue().hasPassword()).isFalse();
        assertThat(user.getValue().getEmail()).startsWith("bot-").endsWith("@bot.invalid");
        assertThat(response.nickname()).isEqualTo("땅땅봇");
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.liveStreamId()).isNull();
    }

    @Test
    @DisplayName("봇 만들기: 이미 쓰는 닉네임이면 USER_002")
    void createDuplicateNickname() {
        given(fileStorage.find(KEY)).willReturn(Optional.of(new FileStorage.StoredFile(1000L, "video/mp4")));
        given(userRepository.existsByNickname("땅땅봇")).willReturn(true);

        assertError(() -> liveBotService.create(new LiveBotCreateRequest("땅땅봇", KEY)), UserErrorCode.DUPLICATE_NICKNAME);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("봇 만들기: 규칙에 맞지 않는 키거나 올라가지 않은 영상이면 거절하고, 형식이 틀린 파일은 지운다")
    void createInvalidVideo() {
        assertError(() -> liveBotService.create(new LiveBotCreateRequest("땅땅봇", "ads/x.mp4")), LiveBotErrorCode.VIDEO_NOT_UPLOADED);

        given(fileStorage.find(KEY)).willReturn(Optional.empty());
        assertError(() -> liveBotService.create(new LiveBotCreateRequest("땅땅봇", KEY)), LiveBotErrorCode.VIDEO_NOT_UPLOADED);

        given(fileStorage.find(KEY)).willReturn(Optional.of(new FileStorage.StoredFile(1000L, "text/html")));
        assertError(() -> liveBotService.create(new LiveBotCreateRequest("땅땅봇", KEY)), LiveBotErrorCode.INVALID_VIDEO);
        verify(fileStorage).delete(KEY);
    }

    @Test
    @DisplayName("켜기: 봇 계정과 봇 영상으로 방송을 시작한다")
    void start() {
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.of(bot()));
        LiveStreamResponse started = LiveStreamResponse.from(LiveStream.createBot(USER_ID, "봇 방송", "설명", KEY), null, null);
        given(liveStreamService.startBot(USER_ID, "봇 방송", "설명", List.of("신발"), KEY)).willReturn(started);

        liveBotService.start(BOT_ID, new LiveBotStartRequest("봇 방송", "설명", List.of("신발")));

        verify(liveStreamService).startBot(USER_ID, "봇 방송", "설명", List.of("신발"), KEY);
    }

    @Test
    @DisplayName("끄기: 켜 둔 방송을 봇 계정으로 종료하고, 켜 둔 방송이 없으면 LIVEBOT_004")
    void end() {
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.of(bot()));
        LiveStream live = LiveStream.createBot(USER_ID, "봇 방송", null, KEY);
        ReflectionTestUtils.setField(live, "id", 100L);
        given(liveStreamRepository.findByActiveSellerId(USER_ID)).willReturn(Optional.of(live));

        liveBotService.end(BOT_ID);
        verify(liveStreamService).end(100L, USER_ID);

        given(liveStreamRepository.findByActiveSellerId(USER_ID)).willReturn(Optional.empty());
        assertError(() -> liveBotService.end(BOT_ID), LiveBotErrorCode.NOT_LIVE);
    }

    @Test
    @DisplayName("지우기: 방송 중이면 거절하고, 아니면 봇과 영상을 지운다 (계정은 남긴다)")
    void delete() {
        LiveBot bot = bot();
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.of(bot));
        given(liveStreamRepository.existsByActiveSellerId(USER_ID)).willReturn(true);
        assertError(() -> liveBotService.delete(BOT_ID), LiveBotErrorCode.LIVE_NOW);
        verify(liveBotRepository, never()).delete(any());

        given(liveStreamRepository.existsByActiveSellerId(USER_ID)).willReturn(false);
        liveBotService.delete(BOT_ID);

        verify(liveBotRepository).delete(bot);
        verify(fileStorage).delete(KEY);
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("프로필 사진: 봇 계정 ID로 회원 프로필 사진과 같은 업로드 URL을 만든다")
    void createProfileImageUpload() {
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.of(bot()));
        ProfileImageUploadRequest request = new ProfileImageUploadRequest("image/png", 1000L);

        liveBotService.createProfileImageUpload(BOT_ID, request);

        verify(userService).createProfileImageUploadUrl(USER_ID, request);
    }

    @Test
    @DisplayName("프로필 사진: 봇 계정의 사진을 바꾸고 바뀐 사진 주소로 응답한다")
    void changeProfileImage() {
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.of(bot()));
        User user = User.createBot("땅땅봇");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(liveStreamRepository.findByActiveSellerId(USER_ID)).willReturn(Optional.empty());
        given(userService.profileImageUrl(user)).willReturn("https://cdn/profile.png");

        LiveBotResponse response = liveBotService.changeProfileImage(BOT_ID, "profile-images/30/x.png");

        verify(userService).changeProfileImage(USER_ID, "profile-images/30/x.png");
        assertThat(response.profileImageUrl()).isEqualTo("https://cdn/profile.png");
    }

    @Test
    @DisplayName("프로필 사진: 없는 봇이면 LIVEBOT_001이고 회원 사진은 건드리지 않는다")
    void changeProfileImageNotFound() {
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.empty());

        assertError(() -> liveBotService.changeProfileImage(BOT_ID, "profile-images/30/x.png"), LiveBotErrorCode.NOT_FOUND);
        verify(userService, never()).changeProfileImage(any(), any());
    }

    @Test
    @DisplayName("프로필 사진 지우기: 봇 계정의 사진을 지운다")
    void deleteProfileImage() {
        given(liveBotRepository.findById(BOT_ID)).willReturn(Optional.of(bot()));

        liveBotService.deleteProfileImage(BOT_ID);

        verify(userService).deleteProfileImage(USER_ID);
    }
}
