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

    // 배송지 수정
    @PatchMapping("/{addressId}")
    ResponseEntity<ApiResponse<UserAddressResponse>> updateAddress(@AuthenticationPrincipal AuthUser authUser,
                                                                   @PathVariable Long addressId,
                                                                   @Valid @RequestBody UserAddressRequest request){
        return ResponseEntity.ok(ApiResponse.ok(userAddressService.updateAddress(authUser.id(),addressId,request)));
    }

    //배송지 삭제(기본 배송지를 지우면, 남은 것 중 가장 최근에 저장한 배송지가 기본인 된다)
    @DeleteMapping("/{addressId}")
    ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal AuthUser authUser,
                                                                   @PathVariable Long addressId){
        userAddressService.deleteAddress(authUser.id(),addressId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
