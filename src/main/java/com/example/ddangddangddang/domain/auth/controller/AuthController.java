package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.dto.*;
import com.example.ddangddangddang.domain.auth.service.AuthService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.RefreshTokenCookieProvider;
import com.example.ddangddangddang.domain.auth.dto.CodeSentResponse;
import com.example.ddangddangddang.domain.auth.dto.PhoneCodeRequest;
import com.example.ddangddangddang.domain.auth.dto.PhoneVerifyRequest;
import com.example.ddangddangddang.domain.auth.dto.VerificationTokenResponse;
import com.example.ddangddangddang.domain.auth.service.SignupVerificationService;
import com.example.ddangddangddang.domain.auth.dto.FindEmailResponse;
import com.example.ddangddangddang.domain.auth.dto.PasswordResetCodeRequest;
import com.example.ddangddangddang.domain.auth.dto.PasswordResetRequest;
import com.example.ddangddangddang.domain.auth.dto.PasswordResetVerifyRequest;
import com.example.ddangddangddang.domain.auth.service.AccountRecoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;
    private final SignupVerificationService signupVerificationService;
    private final AccountRecoveryService accountRecoveryService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(authUser.id(), JwtProvider.resolveBearerToken(authorization));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.expire().toString())
                .body(ApiResponse.ok());
    }

    /**
     * 응답에 회원 정보(user)도 함께 내려준다.
     * 새로고침 등으로 프론트 상태가 초기화됐을 때 재발급 한 번으로 로그인 상태를 복구할 수 있다.
     */
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken) {
        LoginResult result = authService.reissue(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(result.response()));
    }

    @PostMapping("/signup/phone/code")
    public ResponseEntity<ApiResponse<CodeSentResponse>> sendSignupPhoneCode(@Valid @RequestBody PhoneCodeRequest request) {
        long expiresIn = signupVerificationService.sendCode(request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok(new CodeSentResponse(expiresIn)));
    }

    @PostMapping("/signup/phone/verify")
    public ResponseEntity<ApiResponse<VerificationTokenResponse>> verifySignupPhone(@Valid @RequestBody PhoneVerifyRequest request) {
        String token = signupVerificationService.verifyCode(request.phoneNumber(), request.code());
        return ResponseEntity.ok(ApiResponse.ok(new VerificationTokenResponse(token)));
    }

    @PostMapping("/find-email/code")
    public ResponseEntity<ApiResponse<CodeSentResponse>> sendFindEmailCode(@Valid @RequestBody PhoneCodeRequest request) {
        long expiresIn = accountRecoveryService.sendFindEmailCode(request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok(new CodeSentResponse(expiresIn)));
    }

    @PostMapping("/find-email/verify")
    public ResponseEntity<ApiResponse<FindEmailResponse>> findEmail(@Valid @RequestBody PhoneVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(accountRecoveryService.findEmail(request.phoneNumber(), request.code())));
    }

    @PostMapping("/password-reset/code")
    public ResponseEntity<ApiResponse<CodeSentResponse>> sendPasswordResetCode(@Valid @RequestBody PasswordResetCodeRequest request) {
        long expiresIn = accountRecoveryService.sendPasswordResetCode(request.email());
        return ResponseEntity.ok(ApiResponse.ok(new CodeSentResponse(expiresIn)));
    }

    @PostMapping("/password-reset/verify")
    public ResponseEntity<ApiResponse<VerificationTokenResponse>> verifyPasswordResetCode(
            @Valid @RequestBody PasswordResetVerifyRequest request) {
        String token = accountRecoveryService.verifyPasswordResetCode(request.email(), request.code());
        return ResponseEntity.ok(ApiResponse.ok(new VerificationTokenResponse(token)));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        accountRecoveryService.resetPassword(request.verificationToken(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}