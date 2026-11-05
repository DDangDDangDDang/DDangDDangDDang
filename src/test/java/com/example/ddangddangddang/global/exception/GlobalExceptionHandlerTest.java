package com.example.ddangddangddang.global.exception;

import com.example.ddangddangddang.global.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Test
    @DisplayName("유니크 제약 위반(MySQL 1062)은 409 COMMON_009로 응답한다")
    void uniqueViolation() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("duplicate",
                new SQLException("Duplicate entry 'a@test.com' for key 'users.UK_email'", "23000", 1062));

        ResponseEntity<ApiResponse<Void>> response = handler.handleDataIntegrityViolation(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_009");
    }

    @Test
    @DisplayName("Hibernate가 UNIQUE로 알려 준 제약 위반도 409 COMMON_009")
    void uniqueViolationFromHibernate() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("duplicate",
                new ConstraintViolationException("duplicate", new SQLException("dup"), "insert",
                        ConstraintViolationException.ConstraintKind.UNIQUE, "uk_users_nickname"));

        assertThat(handler.handleDataIntegrityViolation(e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("외래 키·NOT NULL 위반, 길이 초과는 중복으로 감추지 않고 500으로 응답한다")
    void otherIntegrityViolations() {
        SQLException[] causes = {
                new SQLException("Cannot add or update a child row: a foreign key constraint fails", "23000", 1452),
                new SQLException("Column 'email' cannot be null", "23000", 1048),
                new SQLException("Data too long for column 'nickname'", "22001", 1406),
        };
        for (SQLException cause : causes) {
            ResponseEntity<ApiResponse<Void>> response =
                    handler.handleDataIntegrityViolation(new DataIntegrityViolationException("violation", cause));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(response.getBody().error().code()).isEqualTo("COMMON_999");
        }
    }
}