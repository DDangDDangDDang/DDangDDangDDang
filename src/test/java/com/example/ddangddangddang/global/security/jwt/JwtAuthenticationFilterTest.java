package com.example.ddangddangddang.global.security.jwt;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserRole;
import com.example.ddangddangddang.global.security.AuthUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtAuthenticationFilterTest.TestController.class)
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @RestController
    static class TestController {

        @GetMapping("/api/v1/test/me")
        public Long me(@AuthenticationPrincipal AuthUser authUser) {
            return authUser.id();
        }

        @GetMapping("/api/v1/admin/test")
        public String admin() {
            return "admin";
        }
    }

    @Test
    @DisplayName("유효한 Access Token이면 AuthUser가 주입된다")
    void validToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createAccessToken(user(1L, UserRole.USER)))))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    @DisplayName("토큰 없이 인증이 필요한 API에 접근하면 401 AUTH_001")
    void noToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("만료된 토큰이면 401 AUTH_002")
    void expiredToken() throws Exception {
        JwtProvider expiredProvider = new JwtProvider(new JwtProperties(
                jwtProperties.secret(), Duration.ofMillis(-1000), Duration.ofDays(14), false
        ));

        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredProvider.createAccessToken(user(1L, UserRole.USER)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("형식이 잘못된 토큰이면 401 AUTH_003")
    void malformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("Refresh Token을 Access Token처럼 쓰면 401 AUTH_003")
    void refreshTokenAsAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createRefreshToken(1L))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("로그아웃된(블랙리스트) 토큰이면 401 AUTH_004")
    void loggedOutToken() throws Exception {
        given(tokenBlacklistRepository.exists(anyString())).willReturn(true);

        mockMvc.perform(get("/api/v1/test/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createAccessToken(user(1L, UserRole.USER)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    @DisplayName("USER가 관리자 API에 접근하면 403 AUTH_006")
    void userAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createAccessToken(user(1L, UserRole.USER)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_006"));
    }

    @Test
    @DisplayName("ADMIN은 관리자 API에 접근할 수 있다")
    void adminAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwtProvider.createAccessToken(user(2L, UserRole.ADMIN)))))
                .andExpect(status().isOk());
    }

    private User user(Long id, UserRole role) {
        User user = User.create("user" + id + "@test.com", "encoded", "회원" + id);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}