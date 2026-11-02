package com.example.ddangddangddang.domain.directmessage.controller;

import com.example.ddangddangddang.domain.directmessage.entity.DmModerationFlag;
import com.example.ddangddangddang.domain.directmessage.repository.DmModerationFlagRepository;
import com.example.ddangddangddang.domain.livechat.moderation.ModerationCategory;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 3단계: 차단, 신고, 관리자 신고 확인 API 테스트
 */
class DmBlockReportControllerTest extends DmApiTestSupport {

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private DmModerationFlagRepository dmModerationFlagRepository;

    @Nested
    @DisplayName("차단")
    class Block {

        @Test
        @DisplayName("차단하면 두 사람 모두 보낼 수 없고(DM_009, DM_010), 차단을 풀면 다시 보낼 수 있다")
        void blockAndUnblock() throws Exception {
            Long roomId = roomId(alice, bob.id());
            sendAndGetId(bob, roomId, "차단 전 메시지");

            block(alice, bob.id())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value(bob.id()))
                    .andExpect(jsonPath("$.data.nickname").value(bob.nickname()));

            send(alice, roomId, UUID.randomUUID().toString(), "보낼 수 있나요")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("DM_009"));
            send(bob, roomId, UUID.randomUUID().toString(), "보낼 수 있나요")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("DM_010"));
            // 대화방과 이전 메시지는 남아 있고, 내 목록에 차단 여부가 보인다.
            getRooms(alice)
                    .andExpect(jsonPath("$.data.content[0].roomId").value(roomId))
                    .andExpect(jsonPath("$.data.content[0].blockedByMe").value(true));
            getRooms(bob).andExpect(jsonPath("$.data.content[0].blockedByMe").value(false));

            mockMvc.perform(delete("/api/v1/dm/blocks/" + bob.id()).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(status().isOk());
            send(bob, roomId, UUID.randomUUID().toString(), "풀렸네요").andExpect(status().isCreated());
        }

        @Test
        @DisplayName("이미 차단한 상대를 다시 차단해도 성공하고, 내 차단 목록에는 한 번만 보인다")
        void blockTwiceAndList() throws Exception {
            Member carol = signupAndLogin("캐롤");
            block(alice, bob.id()).andExpect(status().isOk());
            block(alice, bob.id()).andExpect(status().isOk());
            block(alice, carol.id()).andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/dm/blocks").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].userId").value(carol.id()))
                    .andExpect(jsonPath("$.data[1].userId").value(bob.id()));
        }

        @Test
        @DisplayName("자기 자신은 차단할 수 없고(DM_008), 없는 회원은 DM_004")
        void invalidTarget() throws Exception {
            block(alice, alice.id())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("DM_008"));
            block(alice, 999_999L)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("DM_004"));
        }

        private ResultActions block(Member member, Long userId) throws Exception {
            return mockMvc.perform(post("/api/v1/dm/blocks")
                    .header(HttpHeaders.AUTHORIZATION, member.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"userId\": " + userId + "}"));
        }
    }

    @Nested
    @DisplayName("신고")
    class Report {

        @Test
        @DisplayName("상대의 메시지를 신고하면 201로 신고 내용과 메시지 내용을 응답한다")
        void reportMessage() throws Exception {
            Long roomId = roomId(alice, bob.id());
            Long messageId = sendAndGetId(bob, roomId, "선입금하시면 싸게 드려요");

            report(alice, roomId, messageId, "SCAM", "  선입금 요구  ")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.reporterId").value(alice.id()))
                    .andExpect(jsonPath("$.data.reportedUserId").value(bob.id()))
                    .andExpect(jsonPath("$.data.messageId").value(messageId))
                    .andExpect(jsonPath("$.data.reason").value("SCAM"))
                    .andExpect(jsonPath("$.data.reasonLabel").value("사기·사칭"))
                    .andExpect(jsonPath("$.data.detail").value("선입금 요구"))
                    .andExpect(jsonPath("$.data.reportedContent").value("선입금하시면 싸게 드려요"));
        }

        @Test
        @DisplayName("같은 메시지를 다시 신고하면 DM_012, 대화 전체 신고(messageId 없음)는 할 수 있다")
        void duplicateAndRoomReport() throws Exception {
            Long roomId = roomId(alice, bob.id());
            Long messageId = sendAndGetId(bob, roomId, "스팸");
            report(alice, roomId, messageId, "SPAM", null).andExpect(status().isCreated());

            report(alice, roomId, messageId, "SPAM", null)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("DM_012"));
            report(alice, roomId, null, "HARASSMENT", "계속 연락함")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.messageId").doesNotExist())
                    .andExpect(jsonPath("$.data.reportedContent").doesNotExist());
        }

        @Test
        @DisplayName("내 메시지나 다른 방의 메시지는 신고할 수 없다 (DM_011)")
        void invalidMessage() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long roomId = roomId(alice, bob.id());
            Long mine = sendAndGetId(alice, roomId, "내 메시지");
            Long otherRoomMessage = sendAndGetId(carol, roomId(carol, bob.id()), "다른 방");

            report(alice, roomId, mine, "SPAM", null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("DM_011"));
            report(alice, roomId, otherRoomMessage, "SPAM", null)
                    .andExpect(jsonPath("$.error.code").value("DM_011"));
            report(alice, roomId, 999_999L, "SPAM", null)
                    .andExpect(jsonPath("$.error.code").value("DM_011"));
        }

        @Test
        @DisplayName("참여하지 않은 방은 신고할 수 없고(DM_002), 사유가 없으면 COMMON_001")
        void notMemberOrNoReason() throws Exception {
            Member carol = signupAndLogin("캐롤");
            Long roomId = roomId(alice, bob.id());

            report(carol, roomId, null, "SPAM", null)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("DM_002"));
            mockMvc.perform(post("/api/v1/dm/rooms/" + roomId + "/reports")
                            .header(HttpHeaders.AUTHORIZATION, alice.bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    @Nested
    @DisplayName("관리자 신고 확인")
    class Admin {

        @Test
        @DisplayName("관리자는 신고 목록과 신고된 대화방의 메시지를 볼 수 있다")
        void adminViewsReport() throws Exception {
            Long roomId = roomId(alice, bob.id());
            sendAndGetId(alice, roomId, "사이즈 있나요?");
            Long messageId = sendAndGetId(bob, roomId, "카톡으로 오세요");
            Long reportId = longValue(report(alice, roomId, messageId, "EXTERNAL_TRADE", null), "$.data.reportId");
            String admin = "Bearer " + adminToken();

            mockMvc.perform(get("/api/v1/admin/dm/reports").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].reportId").value(reportId))
                    .andExpect(jsonPath("$.data.content[0].reportedContent").value("카톡으로 오세요"));
            mockMvc.perform(get("/api/v1/admin/dm/reports/" + reportId + "/messages").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].content").value("카톡으로 오세요"))
                    .andExpect(jsonPath("$.data.content[1].content").value("사이즈 있나요?"));
        }

        @Test
        @DisplayName("일반 회원은 관리자 신고 API에 접근할 수 없고(403), 없는 신고는 DM_013")
        void forbiddenAndNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/admin/dm/reports").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/dm/reports/999999/messages")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("DM_013"));
        }

        @Test
        @DisplayName("관리자는 AI가 표시한 1:1 메시지 목록과 그 대화방의 메시지를 볼 수 있고, 없는 기록은 DM_014")
        void adminViewsModerationFlag() throws Exception {
            Long roomId = roomId(alice, bob.id());
            sendAndGetId(alice, roomId, "사이즈 있나요?");
            Long messageId = sendAndGetId(bob, roomId, "직거래하면 더 싸게 드려요");
            Long flagId = dmModerationFlagRepository.save(DmModerationFlag.create(roomId, messageId, bob.id(),
                    ModerationCategory.EXTERNAL_TRADE, "직거래를 제안합니다.")).getId();
            String admin = "Bearer " + adminToken();

            mockMvc.perform(get("/api/v1/admin/dm/moderation-flags").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].flagId").value(flagId))
                    .andExpect(jsonPath("$.data.content[0].categoryLabel").value("외부 거래 유도"))
                    .andExpect(jsonPath("$.data.content[0].reason").value("직거래를 제안합니다."))
                    .andExpect(jsonPath("$.data.content[0].content").value("직거래하면 더 싸게 드려요"));
            mockMvc.perform(get("/api/v1/admin/dm/moderation-flags/" + flagId + "/messages").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].content").value("직거래하면 더 싸게 드려요"))
                    .andExpect(jsonPath("$.data.content[1].content").value("사이즈 있나요?"));
            mockMvc.perform(get("/api/v1/admin/dm/moderation-flags/999999/messages").header(HttpHeaders.AUTHORIZATION, admin))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.code").value("DM_014"));
            mockMvc.perform(get("/api/v1/admin/dm/moderation-flags").header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                    .andExpect(status().isForbidden());
        }

        // Access Token의 권한 클레임만으로 인증하므로, 관리자 권한을 가진 토큰을 직접 만든다.
        private String adminToken() {
            User admin = User.create("admin@test.com", "encoded", "관리자");
            ReflectionTestUtils.setField(admin, "id", 900_000L);
            ReflectionTestUtils.setField(admin, "role", UserRole.ADMIN);
            return jwtProvider.createAccessToken(admin);
        }
    }

    private ResultActions report(Member member, Long roomId, Long messageId, String reason, String detail) throws Exception {
        StringBuilder body = new StringBuilder("{\"reason\": \"" + reason + "\"");
        if (messageId != null) {
            body.append(", \"messageId\": ").append(messageId);
        }
        if (detail != null) {
            body.append(", \"detail\": \"").append(detail).append("\"");
        }
        body.append("}");
        return mockMvc.perform(post("/api/v1/dm/rooms/" + roomId + "/reports")
                .header(HttpHeaders.AUTHORIZATION, member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }
}
