package com.example.ddangddangddang.domain.user.controller;

import com.example.ddangddangddang.domain.auth.dto.CodeSentResponse;
import com.example.ddangddangddang.domain.auth.dto.PhoneCodeRequest;
import com.example.ddangddangddang.domain.user.dto.BadgeEquipRequest;
import com.example.ddangddangddang.domain.user.dto.EmailChangeCodeRequest;
import com.example.ddangddangddang.domain.user.dto.EmailChangeRequest;
import com.example.ddangddangddang.domain.user.dto.MyBadgesResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.dto.PhoneNumberChangeRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageChangeRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadRequest;
import com.example.ddangddangddang.domain.user.dto.ProfileImageUploadResponse;
import com.example.ddangddangddang.domain.user.service.UserBadgeService;
import com.example.ddangddangddang.domain.user.service.UserContactService;
import com.example.ddangddangddang.domain.user.service.UserService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserContactService contactService;
    private final UserBadgeService userBadgeService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyInfo(authUser.id())));
    }

    /** 닉네임·소개 수정. 보낸 항목만 바꾼다. */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoUpdateResponse>> updateMyInfo(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody MyInfoUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMyInfo(authUser.id(), request)));
    }

    /** 프로필 이미지 업로드 URL 받기 (1단계). 받은 URL로 파일을 올린 뒤 PUT /me/profile-image로 확정한다. */
    @PostMapping("/me/profile-image/upload-url")
    public ResponseEntity<ApiResponse<ProfileImageUploadResponse>> createProfileImageUploadUrl(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ProfileImageUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.createProfileImageUploadUrl(authUser.id(), request)));
    }

    /** 올린 이미지를 프로필 이미지로 확정 (2단계) */
    @PutMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<MyInfoResponse>> changeProfileImage(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody ProfileImageChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.changeProfileImage(authUser.id(), request.imageKey())));
    }

    /** 프로필 이미지를 지우고 기본 모양(닉네임 첫 글자)으로 돌아간다. */
    @DeleteMapping("/me/profile-image")
    public ResponseEntity<ApiResponse<MyInfoResponse>> deleteProfileImage(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.deleteProfileImage(authUser.id())));
    }

    /** 새 이메일로 인증번호 보내기 */
    @PostMapping("/me/email/code")
    public ResponseEntity<ApiResponse<CodeSentResponse>> sendEmailChangeCode(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody EmailChangeCodeRequest request) {
        long expiresIn = contactService.sendEmailChangeCode(authUser.id(), request.email());
        return ResponseEntity.ok(ApiResponse.ok(new CodeSentResponse(expiresIn)));
    }

    /** 인증번호를 확인하고 이메일을 바꾼다. 이메일이 담긴 새 Access Token을 함께 준다. */
    @PutMapping("/me/email")
    public ResponseEntity<ApiResponse<MyInfoUpdateResponse>> changeEmail(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody EmailChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(contactService.changeEmail(authUser.id(), request.email(), request.code())));
    }

    /** 새 휴대폰 번호로 인증번호 보내기 */
    @PostMapping("/me/phone-number/code")
    public ResponseEntity<ApiResponse<CodeSentResponse>> sendPhoneNumberChangeCode(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PhoneCodeRequest request) {
        long expiresIn = contactService.sendPhoneNumberChangeCode(authUser.id(), request.phoneNumber());
        return ResponseEntity.ok(ApiResponse.ok(new CodeSentResponse(expiresIn)));
    }

    /** 인증번호를 확인하고 휴대폰 번호를 바꾼다. */
    @PutMapping("/me/phone-number")
    public ResponseEntity<ApiResponse<MyInfoResponse>> changePhoneNumber(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody PhoneNumberChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                contactService.changePhoneNumber(authUser.id(), request.phoneNumber(), request.code())));
    }

    /** 내 닉네임 뱃지: 가진 뱃지와 장착한 뱃지 (뱃지는 마일리지 상점에서 산다) */
    @GetMapping("/me/badges")
    public ResponseEntity<ApiResponse<MyBadgesResponse>> getMyBadges(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(userBadgeService.getMine(authUser.id())));
    }

    /** 가진 뱃지 중 하나를 장착한다. badge가 null이면 뺀다. */
    @PutMapping("/me/badge")
    public ResponseEntity<ApiResponse<MyBadgesResponse>> equipBadge(
            @AuthenticationPrincipal AuthUser authUser, @RequestBody BadgeEquipRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userBadgeService.equip(authUser.id(), request.badge())));
    }
}
