package com.example.ddangddangddang.global.exception;

import com.example.ddangddangddang.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return toResponse(errorCode, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getDefaultMessage()
                : CommonErrorCode.INVALID_INPUT.getMessage();
        return toResponse(CommonErrorCode.INVALID_INPUT, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return toResponse(CommonErrorCode.INVALID_INPUT, "'" + e.getName() + "' 값이 올바르지 않습니다.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameterException(MissingServletRequestParameterException e) {
        return toResponse(CommonErrorCode.INVALID_INPUT, "'" + e.getParameterName() + "' 값이 필요합니다.");
    }

    // 필수 헤더가 빠진 요청. 공개 API(포트원 웹훅 등)를 헤더 없이 부르면 500과 에러 로그가 쌓이지 않게 400으로 응답한다.
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeaderException(MissingRequestHeaderException e) {
        return toResponse(CommonErrorCode.INVALID_INPUT, "'" + e.getHeaderName() + "' 헤더가 필요합니다.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        return toResponse(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadableException(HttpMessageNotReadableException e) {
        return toResponse(CommonErrorCode.INVALID_REQUEST_BODY, CommonErrorCode.INVALID_REQUEST_BODY.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        return toResponse(CommonErrorCode.RESOURCE_NOT_FOUND, CommonErrorCode.RESOURCE_NOT_FOUND.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(CommonErrorCode.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    /**
     * 락 대기 시간 초과(MySQL 1205)와 데드락(MySQL 1213)은 잠시 후 다시 시도하면 성공할 수 있는 실패다.
     * 서버 오류(500)가 아니라 409로 응답해 클라이언트가 재시도할 수 있게 한다.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockFailure(PessimisticLockingFailureException e) {
        log.warn("락 획득 실패: {}", e.getMostSpecificCause().getMessage());
        return toResponse(CommonErrorCode.LOCK_CONFLICT, CommonErrorCode.LOCK_CONFLICT.getMessage());
    }

    /**
     * 유니크 제약 위반만 409로 응답한다. 먼저 조회로 중복을 막지만, 같은 값으로 동시에 들어온 요청(가입 등)은 DB 제약이 막는다.
     * 어느 값이 겹쳤는지는 화면에서 다시 시도하면 조회 검사가 알려 준다.
     * 외래 키·NOT NULL 위반, 길이 초과는 코드 버그라 지금처럼 500으로 응답하고 ERROR로 남긴다. (중복으로 감추지 않음)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (!isUniqueViolation(e)) {
            return handleException(e);
        }
        log.warn("유니크 제약 위반: {}", e.getMostSpecificCause().getMessage());
        return toResponse(CommonErrorCode.DUPLICATE_DATA, CommonErrorCode.DUPLICATE_DATA.getMessage());
    }

    // Hibernate가 알려 주는 제약 종류(UNIQUE)로 보고, 없으면 DB 오류 코드로 본다. (MySQL 1062 Duplicate entry, H2·표준 SQLState 23505)
    static boolean isUniqueViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE) {
                return true;
            }
            if (cause instanceof SQLException sql && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, message));
    }
}