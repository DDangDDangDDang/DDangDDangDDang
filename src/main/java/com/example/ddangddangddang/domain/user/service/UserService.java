package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.user.config.UserPolicyProperties;
import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@Transactional(readOnly = true)
public class UserService {

    static final long PROFILE_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
    static final Duration PROFILE_IMAGE_UPLOAD_TTL = Duration.ofMinutes(5);
    // 받는 이미지 형식과 저장할 때 붙이는 확장자. 로컬 저장소는 확장자로 형식을 정한다. (LocalFileStorage)
    private static final Map<String, String> PROFILE_IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );
    private static final Pattern PROFILE_IMAGE_FILE = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp|gif)");

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final UserPolicyProperties policy;
    private final FileStorage fileStorage;
    private final ChatContentFilter contentFilter;
    private final Clock clock;

    @Autowired
    public UserService(UserRepository userRepository, JwtProvider jwtProvider, UserPolicyProperties policy,
                       FileStorage fileStorage, ChatContentFilter contentFilter) {
        this(userRepository, jwtProvider, policy, fileStorage, contentFilter, Clock.systemDefaultZone());
    }

    UserService(UserRepository userRepository, JwtProvider jwtProvider, UserPolicyProperties policy,
                FileStorage fileStorage, ChatContentFilter contentFilter, Clock clock) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.policy = policy;
        this.fileStorage = fileStorage;
        this.contentFilter = contentFilter;
        this.clock = clock;
    }

    public MyInfoResponse getMyInfo(Long userId) {
        return toMyInfo(findUser(userId));
    }

    /**
     * 닉네임·소개를 바꾸고, 바뀐 닉네임이 담긴 새 Access Token을 함께 돌려준다. 보내지 않은(null) 항목은 그대로 둔다.
     * 닉네임은 바꾼 뒤 일정 기간(기본 30일)이 지나야 다시 바꿀 수 있다. 가입 후 첫 변경은 바로 할 수 있다.
     *
     * 중복은 먼저 조회로 확인하고, 두 회원이 동시에 같은 닉네임으로 바꾸는 경우는 유니크 제약이 막는다.
     * 이 UPDATE에서 바뀌는 유니크 컬럼은 닉네임뿐이라, 제약 위반이면 닉네임 중복으로 응답한다.
     * 변경 감지는 커밋할 때 UPDATE를 보내므로, 제약 위반을 여기서 잡으려면 직접 flush해야 한다.
     */
    @Transactional
    public MyInfoUpdateResponse updateMyInfo(Long userId, MyInfoUpdateRequest request) {
        User user = findUser(userId);
        LocalDateTime now = LocalDateTime.now(clock);

        if (request.bio() != null) {
            String bio = request.bio().strip();
            // 판매자 소개처럼 연락처·외부 링크로 플랫폼 밖 거래를 유도하지 못하게 막는다.
            contentFilter.findViolation(bio).ifPresent(violation -> {
                throw new BusinessException(UserErrorCode.CONTENT_NOT_ALLOWED, violation.getMessage());
            });
            user.changeBio(bio);
        }

        if (request.nickname() != null && !user.getNickname().equals(request.nickname())) {
            if (user.nicknameChangeAvailableAt(policy.nicknameChangeInterval(), now) != null) {
                throw new BusinessException(UserErrorCode.NICKNAME_CHANGE_TOO_SOON);
            }
            changeNickname(user, request.nickname(), now);
        }

        return withNewAccessToken(user);
    }

    /**
     * 닉네임 변경권으로 바꾼다. 변경 제한 기간 중에도 바로 바꾸고, 바꾼 시각부터 다시 제한이 시작된다.
     * 마일리지 결제와 같은 트랜잭션에서 부른다. (ShopService) 여기서 실패하면 마일리지도 빠지지 않는다.
     * - 제한 기간이 아니면(무료로 바꿀 수 있음) 변경권을 쓰지 않게 USER_012
     * - 지금과 같은 닉네임이면 USER_013 (바뀌는 것 없이 마일리지만 빠지지 않게)
     */
    @Transactional
    public MyInfoUpdateResponse changeNicknameWithTicket(Long userId, String nickname) {
        User user = findUser(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.nicknameChangeAvailableAt(policy.nicknameChangeInterval(), now) == null) {
            throw new BusinessException(UserErrorCode.NICKNAME_TICKET_NOT_NEEDED);
        }
        if (user.getNickname().equals(nickname)) {
            throw new BusinessException(UserErrorCode.SAME_NICKNAME);
        }
        changeNickname(user, nickname, now);
        return withNewAccessToken(user);
    }

    /** 다음에 닉네임을 바꿀 수 있는 시각. 지금 바로 바꿀 수 있으면 null */
    public LocalDateTime nicknameChangeAvailableAt(Long userId) {
        return findUser(userId).nicknameChangeAvailableAt(policy.nicknameChangeInterval(), LocalDateTime.now(clock));
    }

    private void changeNickname(User user, String nickname, LocalDateTime now) {
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        user.changeNickname(nickname, now);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }
    }

    /**
     * 프로필 이미지 업로드 URL. 브라우저가 이 URL로 저장소(S3)에 바로 올린 뒤 changeProfileImage로 확정한다.
     * 키에 회원 ID를 넣어, 확정할 때 남이 올린 이미지를 가져다 쓰지 못하게 한다.
     */
    public ProfileImageUploadResponse createProfileImageUploadUrl(Long userId, ProfileImageUploadRequest request) {
        String extension = PROFILE_IMAGE_EXTENSIONS.get(request.contentType());
        if (extension == null) {
            throw new BusinessException(UserErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);
        }
        if (request.contentLength() > PROFILE_IMAGE_MAX_BYTES) {
            throw new BusinessException(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }
        String key = profileImagePrefix(userId) + UUID.randomUUID() + "." + extension;
        String uploadUrl = fileStorage.presignUpload(key, request.contentType(), request.contentLength(), PROFILE_IMAGE_UPLOAD_TTL);
        return new ProfileImageUploadResponse(uploadUrl, key, PROFILE_IMAGE_UPLOAD_TTL.toSeconds());
    }

    /**
     * 올린 이미지를 프로필 이미지로 쓴다. 저장소에 실제로 올라갔는지, 형식·크기가 맞는지 다시 확인한다.
     * 이전 이미지는 커밋된 뒤 지운다. (롤백되면 이전 이미지를 계속 써야 하므로)
     */
    @Transactional
    public MyInfoResponse changeProfileImage(Long userId, String imageKey) {
        String prefix = profileImagePrefix(userId);
        if (!imageKey.startsWith(prefix) || !PROFILE_IMAGE_FILE.matcher(imageKey.substring(prefix.length())).matches()) {
            throw new BusinessException(UserErrorCode.PROFILE_IMAGE_NOT_UPLOADED);
        }
        FileStorage.StoredFile file = fileStorage.find(imageKey)
                .orElseThrow(() -> new BusinessException(UserErrorCode.PROFILE_IMAGE_NOT_UPLOADED));
        if (file.size() > PROFILE_IMAGE_MAX_BYTES) {
            throw new BusinessException(UserErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }
        if (!PROFILE_IMAGE_EXTENSIONS.containsKey(file.contentType())) {
            throw new BusinessException(UserErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE);
        }

        User user = findUser(userId);
        String previous = user.getProfileImageKey();
        user.changeProfileImage(imageKey);
        if (previous != null && !previous.equals(imageKey)) {
            deleteAfterCommit(previous);
        }
        return toMyInfo(user);
    }

    @Transactional
    public MyInfoResponse deleteProfileImage(Long userId) {
        User user = findUser(userId);
        String previous = user.getProfileImageKey();
        user.changeProfileImage(null);
        if (previous != null) {
            deleteAfterCommit(previous);
        }
        return toMyInfo(user);
    }

    /** 화면에 보여 줄 프로필 이미지 주소. 없으면 null */
    public String profileImageUrl(User user) {
        return user.getProfileImageKey() == null ? null : fileStorage.publicUrl(user.getProfileImageKey());
    }

    /** 바뀐 정보와 새 Access Token. 토큰 안에 이메일·닉네임이 들어 있어서 바뀌면 새 토큰으로 바꿔야 한다. */
    MyInfoUpdateResponse withNewAccessToken(User user) {
        String accessToken = jwtProvider.createAccessToken(user);
        return new MyInfoUpdateResponse(toMyInfo(user), accessToken, jwtProvider.getAccessTokenValidity().toSeconds());
    }

    MyInfoResponse toMyInfo(User user) {
        LocalDateTime availableAt = user.nicknameChangeAvailableAt(policy.nicknameChangeInterval(), LocalDateTime.now(clock));
        return MyInfoResponse.of(user, availableAt, profileImageUrl(user));
    }

    User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private static String profileImagePrefix(Long userId) {
        return "profile-images/" + userId + "/";
    }

    // 파일을 못 지워도 프로필 변경은 성공으로 둔다. (남은 파일은 저장소 수명 주기 규칙으로 정리)
    private void deleteAfterCommit(String key) {
        Runnable delete = () -> {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException e) {
                log.warn("이전 프로필 이미지를 지우지 못했습니다. key={}", key, e);
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
