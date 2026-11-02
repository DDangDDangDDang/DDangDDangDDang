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
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    // 내 배송지 목록
    @GetMapping
    ResponseEntity<ApiResponse<List<UserAddressResponse>>> getAddresses(@AuthenticationPrincipal AuthUser authUser){
        return ResponseEntity.ok(ApiResponse.ok(userAddressService.getAddresses(authUser.id())));
    }

    //내 기본 배송지(주문시 자동 채우기용, 저장된 배송지 없을시 data: null)
    @GetMapping("/default")
    ResponseEntity<ApiResponse<UserAddressResponse>> getDefaultAddress(@AuthenticationPrincipal AuthUser authUser){
        return ResponseEntity.ok(ApiResponse.ok(userAddressService.getDefaultAddress(authUser.id())));
    }
}
