package com.example.ddangddangddang.global.config;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
import com.example.ddangddangddang.global.security.SecurityConfig;
import com.example.ddangddangddang.global.security.jwt.TokenBlacklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 명세 파일(docs/api/openapi.json)을 코드와 맞춘다. 프론트엔드는 이 파일로 타입과 API 호출 코드를 만든다.
 *
 * - 로컬: 테스트를 돌리면 명세가 바뀌었을 때 파일을 자동으로 다시 쓴다. 바뀐 파일을 함께 커밋한다.
 * - CI(환경변수 CI=true): 파일을 쓰지 않고 비교만 한다. 커밋하지 않았으면 실패한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSpecTest {

    private static final Path SPEC_FILE = Path.of("docs", "api", "openapi.json");
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Autowired
    private MockMvc mockMvc;

    // 테스트에서 실제 Redis 없이 실행하기 위해 Mock으로 대체
    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    @DisplayName("API 명세와 Swagger UI는 로그인 없이 볼 수 있다")
    void specAndSwaggerUiArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 없이 호출하는 API는 명세에서 인증 불필요로, 나머지는 토큰 필요로 표시된다")
    void publicApisHaveNoSecurity() throws Exception {
        JsonNode spec = currentSpec();
        for (String path : SecurityConfig.PUBLIC_POST_APIS) {
            JsonNode post = spec.path("paths").path(path).path("post");
            assertThat(post.isMissingNode()).as("명세에 POST %s가 없습니다. PUBLIC_POST_APIS의 경로를 확인하세요.", path).isFalse();
            assertThat(post.path("security").isArray() && post.path("security").isEmpty())
                    .as("POST %s는 security가 빈 배열이어야 합니다.", path).isTrue();
        }
        // 보호된 API는 operation에 security를 두지 않아 최상위의 bearerAuth 요구를 따른다.
        assertThat(spec.path("paths").path("/api/v1/auctions/{auctionId}/cancel").path("post").has("security")).isFalse();
        assertThat(spec.path("security").get(0).has(OpenApiConfig.BEARER_AUTH)).isTrue();
    }

    @Test
    @DisplayName("201·204로 응답하는 API는 명세에도 그 코드로 표시된다")
    void nonOkSuccessResponses() throws Exception {
        JsonNode paths = currentSpec().path("paths");
        assertThat(paths.path("/api/v1/auth/signup").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/v1/auctions").path("post").path("responses").has("201")).isTrue();
        assertThat(paths.path("/api/v1/auctions").path("post").path("responses").has("200")).isFalse();
        assertThat(paths.path("/api/v1/auctions/{auctionId}/cancel").path("post").path("responses").has("204")).isTrue();
        assertThat(paths.path("/api/v1/products/{productId}").path("delete").path("responses").has("204")).isTrue();
        assertThat(paths.path("/api/v1/admin/categories/{categoryId}").path("delete").path("responses").has("204")).isTrue();
    }

    private JsonNode currentSpec() throws Exception {
        return JSON.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("docs/api/openapi.json이 지금 코드의 API 명세와 같다")
    void specFileIsUpToDate() throws Exception {
        JsonNode actual = currentSpec();
        JsonNode saved = Files.exists(SPEC_FILE) ? JSON.readTree(Files.readString(SPEC_FILE, StandardCharsets.UTF_8)) : null;

        if (!isCi()) {
            // 로컬: 명세가 바뀌었을 때만 파일을 다시 쓴다. 바뀐 파일은 git status에 보이니 함께 커밋하면 된다.
            if (!actual.equals(saved)) {
                Files.createDirectories(SPEC_FILE.getParent());
                Files.writeString(SPEC_FILE, JSON.writeValueAsString(actual) + "\n", StandardCharsets.UTF_8);
            }
            return;
        }

        // CI: 파일을 쓰지 않고 커밋된 명세가 코드와 같은지만 확인한다.
        assertThat(saved)
                .as("API가 바뀌었는데 %s가 커밋되지 않았습니다. 로컬에서 테스트(./gradlew test)를 돌리면 파일이 갱신되니, 바뀐 파일을 함께 커밋하세요.", SPEC_FILE)
                .isEqualTo(actual);
    }

    /** GitHub Actions 등 CI는 환경변수 CI=true를 넣어 준다. */
    private static boolean isCi() {
        return "true".equalsIgnoreCase(System.getenv("CI"));
    }
}
