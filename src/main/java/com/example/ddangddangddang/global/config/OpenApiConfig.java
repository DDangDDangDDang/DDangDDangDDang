package com.example.ddangddangddang.global.config;

import com.example.ddangddangddang.global.common.ErrorResponse;
import com.example.ddangddangddang.global.exception.ErrorCode;
import com.example.ddangddangddang.global.security.SecurityConfig;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API 명세(OpenAPI) 설정. 명세는 /v3/api-docs, 화면은 /swagger-ui/index.html (운영에서는 꺼 둔다)
 *
 * 프론트엔드가 명세만 보고 정확한 코드를 만들 수 있도록 다음을 명세에 넣는다.
 * - 인증: Authorization: Bearer {Access Token}
 * - 실패 응답 모양: 모든 API의 4XX·5XX 응답은 ApiErrorResponse ({ success: false, data: null, error: { code, message } })
 * - 에러 코드 목록: x-error-codes (코드, HTTP 상태, 기본 메시지). 화면에 보여줄 문구를 정할 때 쓴다.
 *
 * 명세 파일(docs/api/openapi.json)은 OpenApiSpecTest가 최신인지 확인한다.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";
    static final String ERROR_SCHEMA = "ApiErrorResponse";
    private static final String BASE_PACKAGE = "com.example.ddangddangddang";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("땅땅땅 API")
                        .version("v1")
                        .description("라이브 커머스 경매 서비스 땅땅땅의 REST API. 실시간 메시지(STOMP)는 docs/api/README.md 참고"))
                // 실행 환경마다 주소가 달라도 명세 파일이 바뀌지 않도록 상대 경로로 둔다.
                .servers(List.of(new Server().url("/")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("로그인·토큰 재발급 응답의 accessToken")))
                // 기본은 토큰 필요. 토큰 없이 호출하는 API는 publicApiCustomizer가 표시를 지운다.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * SecurityConfig.PUBLIC_POST_APIS(회원가입, 로그인, 토큰 재발급, 결제 웹훅 등)는 operation의 security를 빈 배열로 둔다.
     * OpenAPI에서 빈 배열은 "인증 불필요"라서, 명세로 만든 클라이언트가 로그인 전에 토큰을 요구하지 않는다.
     */
    @Bean
    public OpenApiCustomizer publicApiCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            for (String path : SecurityConfig.PUBLIC_POST_APIS) {
                PathItem item = openApi.getPaths().get(path);
                if (item != null && item.getPost() != null) {
                    item.getPost().setSecurity(List.of());
                }
            }
        };
    }

    /**
     * operationId를 "컨트롤러 이름 + 메서드 이름"으로 정한다. (예: AuctionController.cancelAuction → auctionCancelAuction)
     * 프론트엔드가 생성하는 함수 이름이 되므로, 기본값처럼 메서드 이름이 겹칠 때 creation_1 같은 번호가 붙지 않게 한다.
     */
    @Bean
    public OperationCustomizer operationIdCustomizer() {
        return (operation, handlerMethod) -> {
            String controller = handlerMethod.getBeanType().getSimpleName().replaceFirst("Controller$", "");
            operation.setOperationId(StringUtils.uncapitalize(controller) + StringUtils.capitalize(handlerMethod.getMethod().getName()));
            return operation;
        };
    }

    @Bean
    public OpenApiCustomizer errorResponseCustomizer() {
        return openApi -> {
            Map<String, Schema> errorSchemas = ModelConverters.getInstance().readAll(ErrorResponse.class);
            errorSchemas.forEach(openApi.getComponents()::addSchemas);
            openApi.getComponents().addSchemas(ERROR_SCHEMA, new ObjectSchema()
                    .description("실패 응답. error.code로 원인을 구분한다. (x-error-codes 참고)")
                    .addProperty("success", new BooleanSchema()._default(false))
                    .addProperty("data", new ObjectSchema().nullable(true))
                    .addProperty("error", new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                    .required(List.of("success", "error")));

            if (openApi.getPaths() != null) {
                openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
                    // 컨트롤러가 produces를 적지 않아 */*로 나오는 응답을 실제 형식(JSON)으로 바꾼다.
                    operation.getResponses().values().forEach(response -> {
                        Content content = response.getContent();
                        if (content != null && content.containsKey("*/*")) {
                            content.addMediaType("application/json", content.remove("*/*"));
                        }
                    });
                    operation.getResponses().putIfAbsent("4XX", errorResponse("요청 오류 (검증 실패, 인증 필요, 권한 없음, 없는 리소스, 상태 충돌 등)"));
                    operation.getResponses().putIfAbsent("5XX", errorResponse("서버 오류"));
                }));
            }

            openApi.addExtension("x-error-codes", errorCodes());
        };
    }

    private static ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + ERROR_SCHEMA))));
    }

    /** ErrorCode를 구현한 모든 enum의 값을 코드 순으로 모은다. */
    private static List<Map<String, Object>> errorCodes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));

        List<ErrorCode> codes = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), OpenApiConfig.class.getClassLoader());
            if (type.isEnum() && ErrorCode.class.isAssignableFrom(type)) {
                for (Object constant : type.getEnumConstants()) {
                    codes.add((ErrorCode) constant);
                }
            }
        }

        return codes.stream()
                .sorted(Comparator.comparing(ErrorCode::getCode))
                .map(code -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("code", code.getCode());
                    entry.put("status", code.getStatus().value());
                    entry.put("message", code.getMessage());
                    return entry;
                })
                .toList();
    }
}
