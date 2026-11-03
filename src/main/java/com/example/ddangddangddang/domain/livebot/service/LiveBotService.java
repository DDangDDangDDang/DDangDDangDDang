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
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.domain.livestream.service.LiveStreamService;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.domain.user.service.UserService;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 라이브 봇: 관리자가 만든 판매자 계정이 미리 올린 녹화 영상으로 방송한다. 방송 목록이 비어 보이지 않게 켜 둔다.
 *
 * - 봇을 만들면 로그인할 수 없는 계정(User.createBot)이 생긴다. 방송 카드·시청 화면에는 이 계정의 닉네임이 판매자로 보인다.
 * - 녹화 영상은 다른 파일과 같은 방식으로 올린다. (createVideoUpload로 URL을 받아 브라우저가 올린 뒤 create로 키를 넘김)
 * - 켜면 이 계정의 방송을 만들어 바로 시작한다. 송출하는 사람이 없으므로 시청 화면이 영상을 방송 시작 시각에 맞춰 반복 재생해
 *   누가 언제 들어와도 같은 장면을 본다. 송출 감시(LiveStreamIdleWatcher)는 봇 방송을 끝내지 않는다.
 * - 시청자 수는 사람 방송과 똑같이 실제로 들어온 시청자만 센다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LiveBotService {

    static final long MAX_VIDEO_BYTES = 500L * 1024 * 1024;
    // 큰 영상은 올리는 데 오래 걸리므로 다른 업로드보다 길게 둔다.
    static final Duration UPLOAD_TTL = Duration.ofMinutes(30);
    static final String KEY_PREFIX = "live-bots/";
    /** 받는 영상 형식과 저장할 확장자. 브라우저 <video>가 바로 재생할 수 있는 형식만 받는다 */
    static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm");
    private static final Pattern VIDEO_FILE = Pattern.compile("[0-9a-f-]{36}\\.(mp4|webm)");

    private final LiveBotRepository liveBotRepository;
    private final UserRepository userRepository;
    private final LiveStreamRepository liveStreamRepository;
    private final LiveStreamService liveStreamService;
    private final FileStorage fileStorage;
    // 봇 프로필 사진은 회원이 자기 사진을 바꾸는 것과 같은 규칙·저장 위치를 쓴다. (profile-images/{봇 계정 ID}/...)
    private final UserService userService;

    /** 관리자 목록. 봇마다 지금 켜 둔 방송을 함께 보여 준다 */
    public List<LiveBotResponse> getAll() {
        List<LiveBot> bots = liveBotRepository.findAllByOrderByIdDesc();
        if (bots.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = bots.stream().map(LiveBot::getUserId).toList();
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        // 봇 방송은 만들자마자 시작하므로 종료되지 않은 방송은 곧 방송 중인 방송이다.
        Map<Long, LiveStream> lives = liveStreamRepository.findAllByActiveSellerIdIn(userIds).stream()
                .filter(stream -> stream.getStatus() == LiveStreamStatus.LIVE)
                .collect(Collectors.toMap(LiveStream::getSellerId, Function.identity()));
        return bots.stream().map(bot -> toResponse(bot, users.get(bot.getUserId()), lives.get(bot.getUserId()))).toList();
    }

    /** 녹화 영상 업로드 URL. 500MB 이하의 MP4·WEBM만 받는다 */
    public LiveBotVideoUploadResponse createVideoUpload(LiveBotVideoUploadRequest request) {
        String extension = VIDEO_TYPES.get(request.contentType());
        if (extension == null || request.contentLength() > MAX_VIDEO_BYTES) {
            throw new BusinessException(LiveBotErrorCode.INVALID_VIDEO);
        }
        String key = KEY_PREFIX + UUID.randomUUID() + "." + extension;
        String uploadUrl = fileStorage.presignUpload(key, request.contentType(), request.contentLength(), UPLOAD_TTL);
        return new LiveBotVideoUploadResponse(uploadUrl, key, fileStorage.publicUrl(key), UPLOAD_TTL.toSeconds());
    }

    /** 봇 만들기. 올린 영상이 실제로 있고 규칙에 맞는지 확인한 뒤 로그인할 수 없는 계정을 만든다 */
    @Transactional
    public LiveBotResponse create(LiveBotCreateRequest request) {
        String nickname = request.nickname().strip();
        // 요청 검증(@Size)은 앞뒤 공백까지 세므로 저장할 값으로 한 번 더 확인한다.
        if (nickname.length() < 2 || nickname.length() > 10) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "닉네임은 2~10자여야 합니다.");
        }
        String videoKey = validUploadedVideo(request.videoKey());
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        User user = userRepository.save(User.createBot(nickname));
        LiveBot bot = liveBotRepository.save(LiveBot.create(user.getId(), videoKey));
        log.info("[라이브 봇] 생성 botId={}, userId={}", bot.getId(), user.getId());
        return toResponse(bot, user, null);
    }

    /** 봇 방송 켜기. 봇 계정의 방송을 만들어 바로 시작한다. 이미 켜져 있으면 ACTIVE_STREAM_EXISTS */
    @Transactional
    public LiveStreamResponse start(Long botId, LiveBotStartRequest request) {
        LiveBot bot = findById(botId);
        LiveStreamResponse response = liveStreamService.startBot(
                bot.getUserId(), request.title(), request.description(), request.hashtags(), bot.getVideoKey());
        log.info("[라이브 봇] 방송 시작 botId={}, liveStreamId={}", botId, response.id());
        return response;
    }

    /** 봇 방송 끄기. 판매자가 [방송 종료]를 누른 것과 같다. (채팅 종료 안내, 방 삭제) */
    @Transactional
    public LiveStreamResponse end(Long botId) {
        LiveBot bot = findById(botId);
        LiveStream liveStream = liveStreamRepository.findByActiveSellerId(bot.getUserId())
                .orElseThrow(() -> new BusinessException(LiveBotErrorCode.NOT_LIVE));
        LiveStreamResponse response = liveStreamService.end(liveStream.getId(), bot.getUserId());
        log.info("[라이브 봇] 방송 종료 botId={}, liveStreamId={}", botId, liveStream.getId());
        return response;
    }

    /**
     * 봇 지우기. 방송 중이면 먼저 꺼야 한다. 영상은 커밋 뒤에 지운다.
     * 봇 계정은 남긴다. (끝난 방송·채팅 기록이 이 계정을 판매자로 가리킨다)
     */
    @Transactional
    public void delete(Long botId) {
        LiveBot bot = findById(botId);
        if (liveStreamRepository.existsByActiveSellerId(bot.getUserId())) {
            throw new BusinessException(LiveBotErrorCode.LIVE_NOW);
        }
        liveBotRepository.delete(bot);
        deleteAfterCommit(bot.getVideoKey());
        log.info("[라이브 봇] 삭제 botId={}", botId);
    }

    /** 봇 프로필 사진 업로드 URL. 회원 프로필 사진과 같은 규칙(5MB 이하 JPG·PNG·WEBP·GIF)이다 */
    public ProfileImageUploadResponse createProfileImageUpload(Long botId, ProfileImageUploadRequest request) {
        return userService.createProfileImageUploadUrl(findById(botId).getUserId(), request);
    }

    /** 올린 사진을 봇 프로필 사진으로 쓴다. 이전 사진은 커밋 뒤 지운다. 방송 카드·시청 화면·판매자 프로필에 바로 보인다 */
    @Transactional
    public LiveBotResponse changeProfileImage(Long botId, String imageKey) {
        LiveBot bot = findById(botId);
        userService.changeProfileImage(bot.getUserId(), imageKey);
        return toResponse(bot);
    }

    /** 봇 프로필 사진을 지운다. (화면은 닉네임 첫 글자) */
    @Transactional
    public LiveBotResponse deleteProfileImage(Long botId) {
        LiveBot bot = findById(botId);
        userService.deleteProfileImage(bot.getUserId());
        return toResponse(bot);
    }

    private String validUploadedVideo(String raw) {
        String key = raw == null ? "" : raw.strip();
        if (!key.startsWith(KEY_PREFIX) || !VIDEO_FILE.matcher(key.substring(KEY_PREFIX.length())).matches()) {
            throw new BusinessException(LiveBotErrorCode.VIDEO_NOT_UPLOADED);
        }
        FileStorage.StoredFile file = fileStorage.find(key)
                .orElseThrow(() -> new BusinessException(LiveBotErrorCode.VIDEO_NOT_UPLOADED));
        if (file.size() > MAX_VIDEO_BYTES || !VIDEO_TYPES.containsKey(file.contentType())) {
            fileStorage.delete(key);
            throw new BusinessException(LiveBotErrorCode.INVALID_VIDEO);
        }
        return key;
    }

    private LiveBot findById(Long botId) {
        return liveBotRepository.findById(botId).orElseThrow(() -> new BusinessException(LiveBotErrorCode.NOT_FOUND));
    }

    // 한 봇만 다시 읽어 응답한다. (프로필 사진을 바꾼 뒤)
    private LiveBotResponse toResponse(LiveBot bot) {
        User user = userRepository.findById(bot.getUserId()).orElse(null);
        LiveStream live = liveStreamRepository.findByActiveSellerId(bot.getUserId())
                .filter(stream -> stream.getStatus() == LiveStreamStatus.LIVE)
                .orElse(null);
        return toResponse(bot, user, live);
    }

    private LiveBotResponse toResponse(LiveBot bot, User user, LiveStream live) {
        return new LiveBotResponse(bot.getId(), bot.getUserId(), user == null ? null : user.getNickname(),
                user == null ? null : userService.profileImageUrl(user),
                fileStorage.publicUrl(bot.getVideoKey()),
                live == null ? null : live.getId(),
                live == null ? null : live.getTitle(),
                live == null ? null : live.getStartedAt(),
                bot.getCreatedAt());
    }

    private void deleteAfterCommit(String key) {
        Runnable delete = () -> {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException e) {
                log.warn("지운 라이브 봇의 영상을 지우지 못했습니다. key={}", key, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete.run();
            }
        });
    }
}
