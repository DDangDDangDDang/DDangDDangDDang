package com.example.ddangddangddang.domain.auth.dto;

import java.time.LocalDate;

/**
 * 이메일 찾기, 가려진 이메일만 보여진다.
 */
public record FindEmailResponse(String maskedEmail, LocalDate joinAt) {
}
