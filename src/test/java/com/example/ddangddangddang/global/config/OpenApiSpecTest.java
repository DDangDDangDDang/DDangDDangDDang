package com.example.ddangddangddang.global.config;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository;
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
 * 저장소의 API 명세 파일(docs/api/openapi.json)이 지금 코드와 같은지 확인한다.
 * 프론트엔드는 이 파일로 타입과 API 호출 코드를 만들기 때문에, API를 바꾸고 파일을 갱신하지 않으면 CI에서 실패한다.
 *
 * 갱신: ./gradlew test --tests '*OpenApiSpecTest' -PupdateOpenApi
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
    @DisplayName("docs/api/openapi.json이 지금 코드의 API 명세와 같다")
    void specFileIsUpToDate() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode actual = JSON.readTree(body);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SPEC_FILE.getParent());
            Files.writeString(SPEC_FILE, JSON.writeValueAsString(actual) + "\n", StandardCharsets.UTF_8);
        }

        assertThat(Files.exists(SPEC_FILE))
                .as("%s가 없습니다. ./gradlew test --tests '*OpenApiSpecTest' -PupdateOpenApi 로 만드세요.", SPEC_FILE)
                .isTrue();
        JsonNode expected = JSON.readTree(Files.readString(SPEC_FILE, StandardCharsets.UTF_8));
        assertThat(actual)
                .as("API가 바뀌었는데 %s가 갱신되지 않았습니다. ./gradlew test --tests '*OpenApiSpecTest' -PupdateOpenApi 로 갱신한 뒤 함께 커밋하세요.", SPEC_FILE)
                .isEqualTo(expected);
    }
}
