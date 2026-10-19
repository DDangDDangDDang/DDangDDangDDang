package com.example.ddangddangddang.global.exception;

import com.example.ddangddangddang.global.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("락 대기 시간 초과는 500이 아니라 409 COMMON_006으로 응답한다")
    void lockWaitTimeout() {
        CannotAcquireLockException e = new CannotAcquireLockException("lock wait timeout",
                new SQLException("Lock wait timeout exceeded; try restarting transaction", "40001", 1205));

        ResponseEntity<ApiResponse<Void>> response = handler.handleLockFailure(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_006");
    }
}