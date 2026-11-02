package com.example.ddangddangddang.domain.address.controller;

import com.example.ddangddangddang.domain.address.dto.request.UserAddressRequest;
import com.example.ddangddangddang.domain.address.dto.response.UserAddressResponse;
import com.example.ddangddangddang.domain.address.service.UserAddressService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/addresses")
public class UserAddressController {
    private final UserAddressService userAddressService;

    //배송지 저장
    @PostMapping
    ResponseEntity<ApiResponse<UserAddressResponse>> createAddress(@AuthenticationPrincipal AuthUser authUser,
                                                                  @Valid @RequestBody UserAddressRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(userAddressService.createAddress(authUser.id(),request)));
    }
}
