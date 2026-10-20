package com.example.ddangddangddang.global;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ALB 헬스 체크(/actuator/health/liveness)가 토큰 없이 200을 받는지, 다른 actuator 경로는 열리지 않았는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthCheckTest {

    @Autowired
    private MockMvc mockMvc;

    // 테스트에서 실제 Redis 없이 실행하기 위해 Mock으로 대체
    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    @DisplayName("liveness 헬스 체크는 토큰 없이 UP을 응답한다")
    void livenessWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("헬스 체크 외 actuator 경로는 토큰 없이 접근할 수 없다")
    void otherActuatorPathsRequireAuth() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
