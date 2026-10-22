package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.dto.KakaoLoginRequest;
import com.example.ddangddangddang.domain.auth.dto.KakaoLoginResponse;
import com.example.ddangddangddang.domain.auth.dto.KakaoLoginResult;
import com.example.ddangddangddang.domain.auth.dto.KakaoSignupRequest;
import com.example.ddangddangddang.domain.auth.dto.KakaoSignupResponse;
import com.example.ddangddangddang.domain.auth.dto.KakaoSignupResult;
import com.example.ddangddangddang.domain.auth.service.OAuthService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.jwt.RefreshTokenCookieProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 카카오 로그인.
 * 프론트가 카카오 로그인 페이지로 보내고(state 확인은 프론트에서), 콜백으로 받은 인가 코드를 이 API로 넘긴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/oauth/kakao")
public class OAuthController {

    private final OAuthService oAuthService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<KakaoLoginResponse>> login(@Valid @RequestBody KakaoLoginRequest request) {
        KakaoLoginResult result = oAuthService.loginWithKakao(request.code());
        if (!result.loggedIn()) {
            return ResponseEntity.ok(ApiResponse.ok(result.response()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<KakaoSignupResponse>> signup(@Valid @RequestBody KakaoSignupRequest request) {
        KakaoSignupResult result = oAuthService.signupWithKakao(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }
}