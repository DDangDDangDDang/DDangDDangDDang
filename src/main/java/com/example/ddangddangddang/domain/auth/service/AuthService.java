package com.example.ddangddangddang.domain.auth.service;

import com.example.ddangddangddang.domain.auth.dto.*;
import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.security.AuthErrorCode;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_NICKNAME);
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname()
        );
        User savedUser = userRepository.save(user);

        return SignupResponse.from(savedUser);
    }

    public LoginResult login(LoginRequest request) {
        // 이메일이 없을 때와 비밀번호가 틀릴 때 같은 에러를 던져 계정 존재 여부를 노출하지 않는다
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        return issueTokens(user);
    }

    private LoginResult issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user);
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(user.getId(), refreshToken, jwtProvider.getRefreshTokenValidity());

        long expiresIn = jwtProvider.getAccessTokenValidity().toSeconds();
        return new LoginResult(LoginResponse.of(accessToken, expiresIn, user), refreshToken);
    }
}