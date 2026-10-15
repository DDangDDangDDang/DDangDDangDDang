package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmReportResponse;
import com.example.ddangddangddang.domain.directmessage.service.DmReportService;
import com.example.ddangddangddang.global.common.ApiResponse;
import com.example.ddangddangddang.global.common.CursorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 1:1 채팅 신고 확인. /api/v1/admin/** 은 SecurityConfig에서 ADMIN만 접근할 수 있다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dm/reports")
public class AdminDmReportController {

    private final DmReportService dmReportService;

    @GetMapping
    public ResponseEntity<ApiResponse<CursorResponse<DmReportResponse>>> getReports(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(dmReportService.getReports(cursor, size)));
    }

    /**
     * 신고된 대화방의 메시지. 신고가 있는 방만 볼 수 있다.
     */
    @GetMapping("/{reportId}/messages")
    public ResponseEntity<ApiResponse<CursorResponse<DmMessageResponse>>> getReportedRoomMessages(
            @PathVariable Long reportId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(dmReportService.getReportedRoomMessages(reportId, cursor, size)));
    }
}
