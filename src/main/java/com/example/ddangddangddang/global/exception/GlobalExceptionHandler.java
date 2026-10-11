package com.example.ddangddangddang.global.exception;

import com.example.ddangddangddang.global.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null
                ? fieldError.getDefaultMessage()
                : CommonErrorCode.INVALID_INPUT.getMessage();
        return toResponse(CommonErrorCode.INVALID_INPUT, message);
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

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, message));
    }
}