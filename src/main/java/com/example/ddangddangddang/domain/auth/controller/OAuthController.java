package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.dto.CodeSentResponse;
import com.example.ddangddangddang.domain.auth.dto.SocialLoginRequest;
import com.example.ddangddangddang.domain.auth.dto.SocialLoginResponse;
import com.example.ddangddangddang.domain.auth.dto.SocialLoginResult;
import com.example.ddangddangddang.domain.auth.dto.SocialPhoneCodeRequest;
import com.example.ddangddangddang.domain.auth.dto.SocialSignupRequest;
import com.example.ddangddangddang.domain.auth.dto.SocialSignupResponse;
import com.example.ddangddangddang.domain.auth.dto.SocialSignupResult;
import com.example.ddangddangddang.domain.auth.oauth.OAuthProviderClients;
import com.example.ddangddangddang.domain.auth.service.OAuthService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.jwt.RefreshTokenCookieProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소셜 로그인 (provider: kakao, google).
 * 프론트가 제공자 로그인 페이지로 보내고(state 확인은 프론트에서), 콜백으로 받은 인가 코드를 이 API로 넘긴다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/oauth/{provider}")
public class OAuthController {

    private final OAuthService oAuthService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<SocialLoginResponse>> login(
            @PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
        SocialLoginResult result = oAuthService.login(OAuthProviderClients.parse(provider), request.code());
        if (!result.loggedIn()) {
            return ResponseEntity.ok(ApiResponse.ok(result.response()));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    /**
     * 소셜 가입 화면의 휴대폰 인증번호 발송. 이미 가입된 번호에도 보낸다. (같은 번호의 기존 계정에 소셜 계정을 연결)
     * 인증번호 확인은 일반 가입과 같은 POST /api/v1/auth/signup/phone/verify를 쓴다.
     */
    @PostMapping("/phone/code")
    public ResponseEntity<ApiResponse<CodeSentResponse>> sendPhoneCode(
            @PathVariable String provider, @Valid @RequestBody SocialPhoneCodeRequest request) {
        long expiresIn = oAuthService.sendSignupPhoneCode(
                OAuthProviderClients.parse(provider), request.signupToken(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok(new CodeSentResponse(expiresIn)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SocialSignupResponse>> signup(
            @PathVariable String provider, @Valid @RequestBody SocialSignupRequest request) {
        SocialSignupResult result = oAuthService.signup(OAuthProviderClients.parse(provider), request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }
}