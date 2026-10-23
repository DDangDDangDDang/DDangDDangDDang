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
import com.example.ddangddangddang.domain.auth.dto.CodeSentResponse;
import com.example.ddangddangddang.domain.auth.dto.KakaoPhoneCodeRequest;
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

    /**
     * 카카오 가입 화면의 휴대폰 인증번호 발송. 이미 가입된 번호에도 보낸다. (같은 번호의 기존 계정에 카카오를 연결)
     * 인증번호 확인은 일반 가입과 같은 POST /api/v1/auth/signup/phone/verify를 쓴다.
     */
    @PostMapping("/phone/code")
    public ResponseEntity<ApiResponse<CodeSentResponse>> sendPhoneCode(@Valid @RequestBody KakaoPhoneCodeRequest request) {
        long expiresIn = oAuthService.sendSignupPhoneCode(request.signupToken(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok(new CodeSentResponse(expiresIn)));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<KakaoSignupResponse>> signup(@Valid @RequestBody KakaoSignupRequest request) {
        KakaoSignupResult result = oAuthService.signupWithKakao(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }
}