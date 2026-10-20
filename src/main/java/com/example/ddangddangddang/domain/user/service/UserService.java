package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    public MyInfoResponse getMyInfo(Long userId) {
        return MyInfoResponse.from(findUser(userId));
    }

    /**
     * 닉네임을 바꾸고, 바뀐 닉네임이 담긴 새 Access Token을 함께 돌려준다.
     *
     * 중복은 먼저 조회로 확인하고, 두 회원이 동시에 같은 닉네임으로 바꾸는 경우는 유니크 제약이 막는다.
     * 이 UPDATE에서 바뀌는 유니크 컬럼은 닉네임뿐이라, 제약 위반이면 닉네임 중복으로 응답한다.
     * 변경 감지는 커밋할 때 UPDATE를 보내므로, 제약 위반을 여기서 잡으려면 직접 flush해야 한다.
     */
    @Transactional
    public MyInfoUpdateResponse updateMyInfo(Long userId, MyInfoUpdateRequest request) {
        User user = findUser(userId);

        if (!user.getNickname().equals(request.nickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
            }
            user.changeNickname(request.nickname());
            try {
                userRepository.flush();
            } catch (DataIntegrityViolationException e) {
                throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
            }
        }

        String accessToken = jwtProvider.createAccessToken(user);
        return new MyInfoUpdateResponse(
                MyInfoResponse.from(user), accessToken, jwtProvider.getAccessTokenValidity().toSeconds());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}