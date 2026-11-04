package com.example.ddangddangddang.global.exception;

import com.example.ddangddangddang.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GlobalExceptionHandler가 잡지 못한 오류(주로 보안 필터 단계 예외)가 /error로 넘어왔을 때 응답한다.
 * Spring Boot 기본 오류 응답({timestamp, status, error, path}) 대신 다른 API와 같은 ApiResponse 형식으로,
 * 원래 HTTP 상태 코드는 그대로 둔 채 내려준다.
 * 외부에서 /error를 직접 부르는 요청은 SecurityConfig에서 인증이 필요하므로 여기까지 오지 않는다.
 */
@Hidden
@RestController
public class ApiErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        HttpStatus status = resolveStatus(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        return ResponseEntity.status(status).body(ApiResponse.fail(toErrorCode(status)));
    }

    private static HttpStatus resolveStatus(Object statusCode) {
        if (statusCode instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null && status.isError()) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static ErrorCode toErrorCode(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> CommonErrorCode.RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case SERVICE_UNAVAILABLE -> CommonErrorCode.SERVICE_UNAVAILABLE;
            default -> status.is4xxClientError() ? CommonErrorCode.INVALID_INPUT : CommonErrorCode.INTERNAL_SERVER_ERROR;
        };
    }
}
