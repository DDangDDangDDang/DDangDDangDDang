package com.example.ddangddangddang.domain.auth.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // 테스트에서 실제 Redis 없이 실행하기 위해 Mock으로 대체
    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Nested
    @DisplayName("POST /api/v1/auth/signup")
    class Signup {

        @Test
        @DisplayName("성공하면 201과 회원 정보를 응답한다")
        void success() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody("user@test.com", "Abcd1234!", "경매왕")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.email").value("user@test.com"))
                    .andExpect(jsonPath("$.data.nickname").value("경매왕"));
        }

        @Test
        @DisplayName("비밀번호 규칙을 어기면 400을 응답한다")
        void invalidPassword() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody("user@test.com", "12345678", "경매왕")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 409를 응답한다")
        void duplicateEmail() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(signupBody("user@test.com", "Abcd1234!", "경매왕")));

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupBody("user@test.com", "Abcd1234!", "다른닉네임")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("USER_001"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("성공하면 Body에 Access Token, 쿠키에 Refresh Token을 담아 응답한다")
        void success() throws Exception {
            signup("user@test.com", "Abcd1234!", "경매왕");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("user@test.com", "Abcd1234!")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresIn").value(1800))
                    .andExpect(jsonPath("$.data.user.nickname").value("경매왕"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));

            verify(refreshTokenRepository).save(any(), anyString(), any());
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401 AUTH_008을 응답한다")
        void wrongPassword() throws Exception {
            signup("user@test.com", "Abcd1234!", "경매왕");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("user@test.com", "Wrong1234!")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_008"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/reissue")
    class Reissue {

        @Test
        @DisplayName("쿠키의 Refresh Token으로 새 토큰을 발급한다")
        void success() throws Exception {
            signup("user@test.com", "Abcd1234!", "경매왕");
            String refreshToken = loginAndGetRefreshToken("user@test.com", "Abcd1234!");
            given(refreshTokenRepository.findByUserId(any())).willReturn(Optional.of(refreshToken));

            mockMvc.perform(post("/api/v1/auth/reissue")
                            .cookie(new Cookie("refreshToken", refreshToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.data.user.nickname").value("경매왕"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")));
        }

        @Test
        @DisplayName("만료된 Access Token이 헤더에 남아 있어도 재발급할 수 있다")
        void withExpiredAccessToken() throws Exception {
            signup("user@test.com", "Abcd1234!", "경매왕");
            String refreshToken = loginAndGetRefreshToken("user@test.com", "Abcd1234!");
            given(refreshTokenRepository.findByUserId(any())).willReturn(Optional.of(refreshToken));

            mockMvc.perform(post("/api/v1/auth/reissue")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer expired.access.token")
                            .cookie(new Cookie("refreshToken", refreshToken)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("쿠키가 없으면 401 AUTH_005")
        void noCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/reissue"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_005"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("성공하면 Refresh Token을 삭제하고 Access Token을 블랙리스트에 올리며 쿠키를 만료시킨다")
        void success() throws Exception {
            signup("user@test.com", "Abcd1234!", "경매왕");
            String accessToken = loginAndGetAccessToken("user@test.com", "Abcd1234!");

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

            verify(refreshTokenRepository).deleteByUserId(any());
            verify(tokenBlacklistRepository).save(anyString(), any());
        }

        @Test
        @DisplayName("로그아웃한 Access Token으로 다시 요청하면 401 AUTH_004")
        void reuseLoggedOutToken() throws Exception {
            signup("user@test.com", "Abcd1234!", "경매왕");
            String accessToken = loginAndGetAccessToken("user@test.com", "Abcd1234!");
            given(tokenBlacklistRepository.exists(anyString())).willReturn(true);

            mockMvc.perform(post("/api/v1/auth/logout")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401 AUTH_001")
        void noToken() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        }
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private String loginAndGetRefreshToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andReturn();
        return result.getResponse().getCookie("refreshToken").getValue();
    }

    private void signup(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signupBody(email, password, nickname)));
    }

    private String loginBody(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String signupBody(String email, String password, String nickname) {
        return """
                {"email": "%s", "password": "%s", "nickname": "%s"}
                """.formatted(email, password, nickname);
    }
}