package com.example.ddangddangddang.domain.user.controller;

import com.example.ddangddangddang.domain.user.dto.MyInfoResponse;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateRequest;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.domain.user.service.UserService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMyInfo(authUser.id())));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoUpdateResponse>> updateMyInfo(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody MyInfoUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(userService.updateMyInfo(authUser.id(), request)));
    }
}