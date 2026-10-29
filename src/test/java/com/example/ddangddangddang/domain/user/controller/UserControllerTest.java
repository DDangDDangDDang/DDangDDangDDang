package com.example.ddangddangddang.domain.user.controller;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationPurpose;
import com.example.ddangddangddang.domain.auth.verification.VerificationRateLimitRepository;
import com.example.ddangddangddang.domain.auth.verification.VerificationService;
import com.example.ddangddangddang.global.security.jwt.JwtProvider;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import com.example.ddangddangddang.support.SignupFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @MockitoBean
    private VerificationService verificationService;

    // 인증번호 발송 API의 IP 제한(Redis)을 거치지 않게 한다.
    @MockitoBean
    private VerificationRateLimitRepository verificationRateLimitRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        SignupFixture.stubPhoneVerification(verificationService);
        accessToken = signupAndLogin("user@test.com", "경매왕");
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMyInfo {

        @Test
        @DisplayName("로그인한 회원의 정보를 응답한다")
        void success() throws Exception {
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.email").value("user@test.com"))
                    .andExpect(jsonPath("$.data.nickname").value("경매왕"))
                    .andExpect(jsonPath("$.data.phoneNumber").value(SignupFixture.phoneOf("user@test.com")))
                    .andExpect(jsonPath("$.data.role").value("USER"));
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401 AUTH_001")
        void withoutToken() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me")
    class UpdateMyInfo {

        @Test
        @DisplayName("닉네임을 바꾸면 새 Access Token을 주고, 그 토큰에 바뀐 닉네임이 들어 있다")
        void success() throws Exception {
            String response = update(accessToken, "새닉네임")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.data.expiresIn").value(1800))
                    .andReturn().getResponse().getContentAsString();

            String newAccessToken = JsonPath.read(response, "$.data.accessToken");
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                    .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
            assertThat(nicknameClaim(newAccessToken)).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("닉네임을 바꾸면 30일 뒤까지 다시 바꿀 수 없다: 429 USER_005, 내 정보에 다음 변경 가능 시각")
        void tooSoon() throws Exception {
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                    .andExpect(jsonPath("$.data.nicknameChangeAvailableAt").doesNotExist());
            String response = update(accessToken, "새닉네임")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.nicknameChangeAvailableAt").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            String newAccessToken = JsonPath.read(response, "$.data.accessToken");

            update(newAccessToken, "또새닉네임")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error.code").value("USER_005"));
            mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
                    .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.data.nicknameChangeAvailableAt").isNotEmpty());
        }

        @Test
        @DisplayName("다른 회원이 쓰는 닉네임이면 409 USER_002")
        void duplicate() throws Exception {
            signupAndLogin("other@test.com", "남의닉네임");

            update(accessToken, "남의닉네임")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("USER_002"));
        }

        @Test
        @DisplayName("닉네임이 2~10자가 아니면 400 COMMON_001")
        void invalidLength() throws Exception {
            update(accessToken, "가")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me 소개")
    class UpdateBio {

        @Test
        @DisplayName("소개만 보내면 닉네임은 그대로 두고 소개를 바꾼다")
        void bioOnly() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"bio": "빈티지 카메라를 모아요"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.nickname").value("경매왕"))
                    .andExpect(jsonPath("$.data.user.bio").value("빈티지 카메라를 모아요"))
                    .andExpect(jsonPath("$.data.user.nicknameChangeAvailableAt").doesNotExist());
        }

        @Test
        @DisplayName("150자를 넘으면 400 COMMON_001")
        void tooLong() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"bio": "%s"}
                                    """.formatted("가".repeat(151))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    @Nested
    @DisplayName("프로필 이미지 (로컬 저장소)")
    class ProfileImage {

        @Test
        @DisplayName("업로드 URL 받기 → 토큰 없이 PUT → 확정하면 내 정보와 판매자 프로필에 이미지 주소가 나오고, 지우면 내 정보에서 사라진다")
        void uploadFlow() throws Exception {
            byte[] image = {1, 2, 3, 4};
            String response = mockMvc.perform(post("/api/v1/users/me/profile-image/upload-url")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"contentType": "image/png", "contentLength": 4}
                                    """))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String uploadUrl = JsonPath.read(response, "$.data.uploadUrl");
            String imageKey = JsonPath.read(response, "$.data.imageKey");

            mockMvc.perform(put(uploadUrl).contentType(MediaType.IMAGE_PNG).content(image))
                    .andExpect(status().isOk());

            String changed = mockMvc.perform(put("/api/v1/users/me/profile-image")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageKey": "%s"}
                                    """.formatted(imageKey)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String imageUrl = JsonPath.read(changed, "$.data.profileImageUrl");
            Integer userId = JsonPath.read(changed, "$.data.userId");

            mockMvc.perform(get(imageUrl))
                    .andExpect(status().isOk())
                    .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(image));
            mockMvc.perform(get("/api/v1/sellers/{sellerId}/profile", userId))
                    .andExpect(jsonPath("$.data.profileImageUrl").value(imageUrl));

            mockMvc.perform(delete("/api/v1/users/me/profile-image").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.profileImageUrl").doesNotExist());
            // 파일 삭제는 커밋 뒤에 한다. 이 테스트는 롤백되므로 UserServiceTest에서 확인한다.
        }

        @Test
        @DisplayName("올리지 않은 이미지로 확정하면 400 USER_009")
        void notUploaded() throws Exception {
            mockMvc.perform(put("/api/v1/users/me/profile-image")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"imageKey": "profile-images/999/123e4567-e89b-12d3-a456-426614174000.png"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("USER_009"));
        }

        @Test
        @DisplayName("없는 업로드 URL로 올리면 404")
        void unknownUploadUrl() throws Exception {
            mockMvc.perform(put("/api/v1/local-storage/uploads/unknown").contentType(MediaType.IMAGE_PNG).content(new byte[]{1}))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("이메일·휴대폰 번호 변경")
    class ChangeContact {

        @Test
        @DisplayName("새 이메일로 인증번호를 받고 확인하면 이메일이 바뀌고, 새 토큰으로 바뀐 이메일로 로그인할 수 있다")
        void changeEmail() throws Exception {
            given(verificationService.issueCode(VerificationPurpose.CHANGE_EMAIL, "new@test.com")).willReturn("123456");
            given(verificationService.verifyCode(VerificationPurpose.CHANGE_EMAIL, "new@test.com", "123456")).willReturn("token");

            mockMvc.perform(post("/api/v1/users/me/email/code")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "new@test.com"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.expiresIn").value(300));

            String response = mockMvc.perform(put("/api/v1/users/me/email")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "new@test.com", "code": "123456"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.user.email").value("new@test.com"))
                    .andReturn().getResponse().getContentAsString();
            String newAccessToken = JsonPath.read(response, "$.data.accessToken");
            assertThat(jwtProvider.parseClaims(newAccessToken).get(JwtProvider.CLAIM_EMAIL, String.class)).isEqualTo("new@test.com");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "new@test.com", "password": "Abcd1234!"}
                                    """))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("다른 회원의 이메일로는 인증번호를 보내지 않는다: 409 USER_001")
        void duplicateEmail() throws Exception {
            signupAndLogin("other@test.com", "다른회원");

            mockMvc.perform(post("/api/v1/users/me/email/code")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "other@test.com"}
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("USER_001"));
        }

        @Test
        @DisplayName("새 번호로 받은 인증번호를 확인하면 휴대폰 번호가 바뀐다")
        void changePhoneNumber() throws Exception {
            given(verificationService.verifyCode(eq(VerificationPurpose.CHANGE_PHONE), eq("01099998888"), eq("123456")))
                    .willReturn("token");

            mockMvc.perform(put("/api/v1/users/me/phone-number")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phoneNumber": "01099998888", "code": "123456"}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.phoneNumber").value("01099998888"));
        }

        @Test
        @DisplayName("인증번호가 6자리 숫자가 아니면 400 COMMON_001")
        void invalidCodeFormat() throws Exception {
            mockMvc.perform(put("/api/v1/users/me/phone-number")
                            .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"phoneNumber": "01099998888", "code": "12ab"}
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    private ResultActions update(String token, String nickname) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "%s"}
                        """.formatted(nickname)));
    }

    private String signupAndLogin(String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SignupFixture.signupBody(email, "Abcd1234!", nickname)))
                .andExpect(status().isCreated());
        String login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Abcd1234!"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(login, "$.data.accessToken");
    }

    private String nicknameClaim(String token) {
        return jwtProvider.parseClaims(token).get(JwtProvider.CLAIM_NICKNAME, String.class);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}