package com.example.ddangddangddang.domain.ad.controller;

import com.example.ddangddangddang.domain.ad.dto.AdResponse;
import com.example.ddangddangddang.domain.ad.entity.AdPlacement;
import com.example.ddangddangddang.domain.ad.service.AdService;
import com.example.ddangddangddang.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 화면 배너 광고. 로그인하지 않아도 볼 수 있다. (SecurityConfig.PUBLIC_GET_APIS) */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ads")
public class AdController {

    private final AdService adService;

    /** 이 자리의 켜진 광고 (먼저 등록한 순). 2개 이상이면 화면이 번갈아 보여 준다 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdResponse>>> getActive(@RequestParam AdPlacement placement) {
        return ResponseEntity.ok(ApiResponse.ok(adService.getActive(placement)));
    }
}
