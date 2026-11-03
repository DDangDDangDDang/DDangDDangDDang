package com.example.ddangddangddang.domain.ad.controller;

import com.example.ddangddangddang.domain.ad.dto.AdCreateRequest;
import com.example.ddangddangddang.domain.ad.dto.AdEnabledRequest;
import com.example.ddangddangddang.domain.ad.dto.AdImageUploadRequest;
import com.example.ddangddangddang.domain.ad.dto.AdImageUploadResponse;
import com.example.ddangddangddang.domain.ad.dto.AdResponse;
import com.example.ddangddangddang.domain.ad.entity.AdPlacement;
import com.example.ddangddangddang.domain.ad.service.AdService;
import com.example.ddangddangddang.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 관리자 광고 관리. /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 통과한다 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ads")
public class AdminAdController {

    private final AdService adService;

    /** 광고 목록. placement가 없으면 모든 자리 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdResponse>>> getAll(@RequestParam(required = false) AdPlacement placement) {
        return ResponseEntity.ok(ApiResponse.ok(adService.getAll(placement)));
    }

    /** 광고 이미지 업로드 URL */
    @PostMapping("/image-uploads")
    public ResponseEntity<ApiResponse<AdImageUploadResponse>> createUpload(@Valid @RequestBody AdImageUploadRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adService.createUpload(request)));
    }

    /** 광고 등록 (켜진 상태) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AdResponse>> create(@Valid @RequestBody AdCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(adService.create(request)));
    }

    /** 켜기·끄기 */
    @PatchMapping("/{adId}")
    public ResponseEntity<ApiResponse<AdResponse>> changeEnabled(@PathVariable Long adId, @Valid @RequestBody AdEnabledRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(adService.changeEnabled(adId, request.enabled())));
    }

    /** 삭제 (이미지도 지운다) */
    @DeleteMapping("/{adId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(@PathVariable Long adId) {
        adService.delete(adId);
        return ResponseEntity.noContent().build();
    }
}
