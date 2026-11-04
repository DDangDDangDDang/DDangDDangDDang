package com.example.ddangddangddang.global.exception;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 클라이언트 잘못으로 난 요청 오류가 500(서버 오류)으로 바뀌지 않는지 실제 API로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    @DisplayName("공개 API인 포트원 웹훅을 서명 헤더 없이 부르면 500이 아니라 400 COMMON_001")
    void missingRequiredHeader() throws Exception {
        mockMvc.perform(post("/api/v1/points/charges/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"))
                .andExpect(jsonPath("$.error.message").value("'webhook-id' 헤더가 필요합니다."));
    }

    @Test
    @DisplayName("JSON을 받는 API에 다른 형식으로 보내면 500이 아니라 415 COMMON_007")
    void unsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("email=a&password=b"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("COMMON_007"));
    }
}
