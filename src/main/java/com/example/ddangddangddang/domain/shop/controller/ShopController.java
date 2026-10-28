package com.example.ddangddangddang.domain.shop.controller;

import com.example.ddangddangddang.domain.shop.dto.NicknameChangeTicketRequest;
import com.example.ddangddangddang.domain.shop.dto.ShopItemResponse;
import com.example.ddangddangddang.domain.shop.service.ShopService;
import com.example.ddangddangddang.domain.user.dto.MyInfoUpdateResponse;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 마일리지 상점 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shop")
public class ShopController {

    private final ShopService shopService;

    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<ShopItemResponse>>> getItems(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.getItems(authUser.id())));
    }

    /** 닉네임 변경권을 사면서 바로 닉네임을 바꾼다. 응답은 내 정보 수정과 같다. (바뀐 닉네임이 담긴 새 Access Token) */
    @PostMapping("/nickname-change")
    public ResponseEntity<ApiResponse<MyInfoUpdateResponse>> buyNicknameChange(
            @AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody NicknameChangeTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(shopService.buyNicknameChange(authUser.id(), request.nickname())));
    }
}
