package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.config.UserPolicyProperties;
import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final UserPolicyProperties policy;
    private final Clock clock;

    @Autowired
    public UserService(UserRepository userRepository, JwtProvider jwtProvider, UserPolicyProperties policy) {
        this(userRepository, jwtProvider, policy, Clock.systemDefaultZone());
    }

    UserService(UserRepository userRepository, JwtProvider jwtProvider, UserPolicyProperties policy, Clock clock) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.policy = policy;
        this.clock = clock;
    }

    public MyInfoResponse getMyInfo(Long userId) {
        return toMyInfo(findUser(userId), LocalDateTime.now(clock));
    }

    /**
     * 닉네임을 바꾸고, 바뀐 닉네임이 담긴 새 Access Token을 함께 돌려준다.
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

        if (!user.getNickname().equals(request.nickname())) {
            if (user.nicknameChangeAvailableAt(policy.nicknameChangeInterval(), now) != null) {
                throw new BusinessException(UserErrorCode.NICKNAME_CHANGE_TOO_SOON);
            }
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
            }
            user.changeNickname(request.nickname(), now);
            try {
                userRepository.flush();
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
            }
        }

        String accessToken = jwtProvider.createAccessToken(user);
        return new MyInfoUpdateResponse(
                toMyInfo(user, now), accessToken, jwtProvider.getAccessTokenValidity().toSeconds());
    }

    private MyInfoResponse toMyInfo(User user, LocalDateTime now) {
        return MyInfoResponse.of(user, user.nicknameChangeAvailableAt(policy.nicknameChangeInterval(), now));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}